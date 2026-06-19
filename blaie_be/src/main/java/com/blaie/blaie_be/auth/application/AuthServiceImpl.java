package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.api.response.AuthUserResponse;
import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
import com.blaie.blaie_be.auth.application.command.RegisterLocalCommand;
import com.blaie.blaie_be.auth.application.result.WebAuthResult;
import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityRepository;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthPersistenceExceptionTranslator;
import com.blaie.blaie_be.auth.infrastructure.persistence.RefreshTokenEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.RefreshTokenRepository;
import com.blaie.blaie_be.auth.infrastructure.persistence.UserEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.UserRepository;
import com.blaie.blaie_be.auth.infrastructure.security.AuthProperties;
import com.blaie.blaie_be.auth.infrastructure.security.AuthTokenService;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String DUMMY_PASSWORD = "BlaieDummyPasswordThatCannotAuthenticate";

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;
    private final AuthTokenService authTokenService;
    private final AuthProperties authProperties;

    public AuthServiceImpl(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            AuthProperties authProperties
    ) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
        this.authTokenService = authTokenService;
        this.authProperties = authProperties;
    }

    @Transactional
    @Override
    public WebAuthResult registerLocal(RegisterLocalCommand command, String userAgent) {
        String username = trim(command.username());
        String email = trim(command.email());
        String displayName = trim(command.displayName());
        String usernameNormalized = normalize(username);
        String emailNormalized = normalize(email);

        if (authIdentityRepository.existsByProviderAndUsernameNormalized(AuthConstants.PROVIDER_LOCAL, usernameNormalized)
                || userRepository.existsByUsernameNormalized(usernameNormalized)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (authIdentityRepository.existsByProviderAndEmailNormalized(AuthConstants.PROVIDER_LOCAL, emailNormalized)
                || userRepository.existsByEmailNormalized(emailNormalized)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        try {
            UserEntity user = userRepository.saveAndFlush(
                    UserEntity.localUser(username, usernameNormalized, email, emailNormalized, displayName)
            );
            String passwordHash = passwordEncoder.encode(command.password());
            authIdentityRepository.saveAndFlush(
                    AuthIdentityEntity.local(user, usernameNormalized, emailNormalized, passwordHash)
            );
            return issueWebAuth(user, UUID.randomUUID(), userAgent);
        } catch (DataIntegrityViolationException exception) {
            throw AuthPersistenceExceptionTranslator.translateRegistrationDuplicate(exception);
        }
    }

    @Transactional
    @Override
    public WebAuthResult loginLocal(LoginLocalCommand command, String userAgent) {
        String identifierNormalized = normalize(command.identifier());
        Optional<AuthIdentityEntity> identity = findLocalIdentity(identifierNormalized);
        String passwordHash = identity.map(AuthIdentityEntity::passwordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(command.password(), passwordHash);
        if (identity.isEmpty() || !passwordMatches) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        UserEntity user = identity.orElseThrow().user();
        if (!AuthConstants.USER_STATUS_ACTIVE.equals(user.status())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return issueWebAuth(user, UUID.randomUUID(), userAgent);
    }

    @Transactional(noRollbackFor = AppException.class)
    @Override
    public WebAuthResult refreshWeb(String refreshToken, String userAgent) {
        RefreshTokenEntity currentToken = requireRefreshToken(refreshToken);
        Instant now = Instant.now();
        if (currentToken.isRevoked()) {
            refreshTokenRepository.revokeFamily(currentToken.tokenFamilyId(), "family_breach", now);
            throw new AppException(ErrorCode.SESSION_REVOKED);
        }
        if (currentToken.isExpired(now)) {
            currentToken.revoke("expired", null, now);
            throw new AppException(ErrorCode.SESSION_EXPIRED);
        }

        UserEntity user = currentToken.user();
        if (!AuthConstants.USER_STATUS_ACTIVE.equals(user.status())) {
            refreshTokenRepository.revokeAllUserTokens(user.id(), "user_not_active", now);
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        String nextRefreshTokenValue = authTokenService.generateRefreshToken();
        RefreshTokenEntity nextRefreshToken = refreshTokenRepository.save(RefreshTokenEntity.webCookie(
                user,
                authTokenService.hashRefreshToken(nextRefreshTokenValue),
                currentToken.tokenFamilyId(),
                now.plus(authProperties.refreshTokenTtl()),
                userAgent
        ));
        currentToken.markUsed(now);
        currentToken.revoke("rotated", nextRefreshToken, now);
        return new WebAuthResult(
                user.toAuthUserResponse(),
                authTokenService.issueAccessToken(user.id()),
                authProperties.accessTokenTtl(),
                nextRefreshTokenValue,
                authProperties.refreshTokenTtl()
        );
    }

    @Transactional
    @Override
    public void logoutWeb(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String tokenHash = authTokenService.hashRefreshToken(refreshToken);
        Optional<RefreshTokenEntity> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            return;
        }
        RefreshTokenEntity currentToken = tokenOpt.get();
        if (currentToken.isRevoked()) {
            return;
        }
        Instant now = Instant.now();
        currentToken.markUsed(now);
        currentToken.revoke("logout", null, now);
    }

    @Transactional(readOnly = true)
    @Override
    public AuthUserResponse currentUser() {
        UUID userId = CurrentUserHolder.current()
                .map(CurrentUser::userId)
                .map(this::parseUserId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        return userRepository.findByIdAndStatus(userId, AuthConstants.USER_STATUS_ACTIVE)
                .map(UserEntity::toAuthUserResponse)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }

    private WebAuthResult issueWebAuth(UserEntity user, UUID tokenFamilyId, String userAgent) {
        String refreshTokenValue = authTokenService.generateRefreshToken();
        RefreshTokenEntity refreshToken = RefreshTokenEntity.webCookie(
                user,
                authTokenService.hashRefreshToken(refreshTokenValue),
                tokenFamilyId,
                Instant.now().plus(authProperties.refreshTokenTtl()),
                userAgent
        );
        refreshTokenRepository.save(refreshToken);
        return new WebAuthResult(
                user.toAuthUserResponse(),
                authTokenService.issueAccessToken(user.id()),
                authProperties.accessTokenTtl(),
                refreshTokenValue,
                authProperties.refreshTokenTtl()
        );
    }

    private Optional<AuthIdentityEntity> findLocalIdentity(String identifierNormalized) {
        List<AuthIdentityEntity> identities = authIdentityRepository.findAllByProviderAndIdentifier(
                AuthConstants.PROVIDER_LOCAL,
                identifierNormalized
        );
        return identities.size() == 1 ? Optional.of(identities.getFirst()) : Optional.empty();
    }

    private RefreshTokenEntity requireRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return refreshTokenRepository.findByTokenHash(authTokenService.hashRefreshToken(refreshToken))
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException exception) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String normalize(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

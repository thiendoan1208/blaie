package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
import com.blaie.blaie_be.auth.application.command.RegisterLocalCommand;
import com.blaie.blaie_be.auth.application.command.UpdatePasswordCommand;
import com.blaie.blaie_be.auth.application.command.UpdateUsernameCommand;
import com.blaie.blaie_be.auth.application.port.GoogleOAuthProfile;
import com.blaie.blaie_be.auth.application.result.AuthUserResult;
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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final EmailVerificationService emailVerificationService;
    private final AuthProperties authProperties;
    private final Clock clock;

    public AuthServiceImpl(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            EmailVerificationService emailVerificationService,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
        this.authTokenService = authTokenService;
        this.emailVerificationService = emailVerificationService;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Transactional
    @Override
    public WebAuthResult registerLocal(RegisterLocalCommand command, String userAgent) {
        String username = trim(command.username());
        String email = trim(command.email());
        String displayName = trim(command.displayName());
        String password = trim(command.password());
        String usernameNormalized = normalize(username);
        String emailNormalized = normalize(email);

        if (userRepository.existsByUsernameNormalized(usernameNormalized)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmailNormalized(emailNormalized)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        try {
            UserEntity user = userRepository.saveAndFlush(
                    UserEntity.localUser(username, usernameNormalized, email, emailNormalized, displayName)
            );
            String passwordHash = passwordEncoder.encode(password);
            authIdentityRepository.saveAndFlush(
                    AuthIdentityEntity.local(user, passwordHash)
            );
            emailVerificationService.sendInitialVerification(user);
            return issueWebAuth(user, UUID.randomUUID(), userAgent);
        } catch (DataIntegrityViolationException exception) {
            throw AuthPersistenceExceptionTranslator.translateRegistrationDuplicate(exception);
        }
    }

    @Transactional
    @Override
    public WebAuthResult loginLocal(LoginLocalCommand command, String userAgent) {
        String identifierNormalized = normalize(command.identifier());
        AuthIdentityEntity identity = findLocalIdentity(identifierNormalized).orElse(null);
        String passwordHash = identity == null ? dummyPasswordHash : identity.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(trim(command.password()), passwordHash);

        if (identity == null || !passwordMatches) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        
        UserEntity user = identity.user();
        
        if (!AuthConstants.USER_STATUS_ACTIVE.equals(user.status())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        
        return issueWebAuth(user, UUID.randomUUID(), userAgent);
    }

    @Transactional
    @Override
    public WebAuthResult loginGoogle(GoogleOAuthProfile profile, String userAgent) {
        String subject = trim(profile.subject());
        String email = trim(profile.email());
        String emailNormalized = normalize(email);
        if (subject.isBlank() || emailNormalized.isBlank() || !profile.emailVerified()) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }

        UserEntity user = authIdentityRepository
                .findByProviderAndProviderSubject(AuthConstants.PROVIDER_GOOGLE, subject)
                .map(AuthIdentityEntity::user)
                .orElseGet(() -> linkOrCreateGoogleUser(profile, subject, email, emailNormalized));

        if (!AuthConstants.USER_STATUS_ACTIVE.equals(user.status())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        return issueWebAuth(user, UUID.randomUUID(), userAgent);
    }

    @Transactional(noRollbackFor = AppException.class)
    @Override
    public WebAuthResult refreshWeb(String refreshToken, String userAgent) {
        RefreshTokenEntity currentToken = requireRefreshToken(refreshToken);
        Instant now = clock.instant();
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
                toAuthUserResult(user),
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
        Instant now = clock.instant();
        currentToken.markUsed(now);
        currentToken.revoke("logout", null, now);
    }

    @Transactional(readOnly = true)
    @Override
    public AuthUserResult currentUser() {
        return toAuthUserResult(requireCurrentActiveUser());
    }

    @Transactional
    @Override
    public AuthUserResult updateUsername(UpdateUsernameCommand command) {
        UserEntity user = requireCurrentActiveUser();
        String username = trim(command.username());
        String usernameNormalized = normalize(username);
        if (usernameNormalized.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Validation failed", Map.of(
                    "username", List.of("Username is required")
            ));
        }
        if (usernameNormalized.equals(normalize(user.username()))) {
            return toAuthUserResult(user);
        }
        userRepository.findByUsernameNormalized(usernameNormalized)
                .filter(existingUser -> !existingUser.id().equals(user.id()))
                .ifPresent(existingUser -> {
                    throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
                });
        user.updateUsername(username, usernameNormalized);
        return toAuthUserResult(user);
    }

    @Transactional
    @Override
    public AuthUserResult updatePassword(UpdatePasswordCommand command) {
        UserEntity user = requireCurrentActiveUser();
        String newPassword = trim(command.newPassword());
        Optional<AuthIdentityEntity> localIdentity = authIdentityRepository.findByUser_IdAndProvider(
                user.id(),
                AuthConstants.PROVIDER_LOCAL
        );

        if (localIdentity.isPresent()) {
            String currentPassword = trim(command.currentPassword());
            if (currentPassword.isBlank()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Validation failed", Map.of(
                        "currentPassword", List.of("Current password is required")
                ));
            }
            AuthIdentityEntity identity = localIdentity.get();
            if (!passwordEncoder.matches(currentPassword, identity.passwordHash())) {
                throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect", Map.of(
                        "currentPassword", List.of("Current password is incorrect")
                ));
            }
            identity.updatePasswordHash(passwordEncoder.encode(newPassword));
        } else {
            authIdentityRepository.save(AuthIdentityEntity.local(user, passwordEncoder.encode(newPassword)));
        }

        refreshTokenRepository.revokeAllUserTokens(user.id(), "password_changed", clock.instant());
        return toAuthUserResult(user);
    }
    
    private WebAuthResult issueWebAuth(UserEntity user, UUID tokenFamilyId, String userAgent) {
        String refreshTokenValue = authTokenService.generateRefreshToken();
        RefreshTokenEntity refreshToken = RefreshTokenEntity.webCookie(
                user,
                authTokenService.hashRefreshToken(refreshTokenValue),
                tokenFamilyId,
                clock.instant().plus(authProperties.refreshTokenTtl()),
                userAgent
        );
        refreshTokenRepository.save(refreshToken);
        return new WebAuthResult(
                toAuthUserResult(user),
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

    private UserEntity linkOrCreateGoogleUser(
            GoogleOAuthProfile profile,
            String subject,
            String email,
            String emailNormalized
    ) {
        UserEntity user = userRepository.findByEmailNormalized(emailNormalized)
                .orElseGet(() -> userRepository.saveAndFlush(UserEntity.googleUser(
                        email,
                        emailNormalized,
                        displayName(profile, email),
                        trimToNull(profile.avatarUrl())
                )));
        authIdentityRepository.saveAndFlush(AuthIdentityEntity.google(user, subject));
        return user;
    }

    private String displayName(GoogleOAuthProfile profile, String email) {
        String displayName = trim(profile.displayName());
        if (!displayName.isBlank()) {
            return displayName.length() > 100 ? displayName.substring(0, 100) : displayName;
        }
        int atIndex = email.indexOf('@');
        String fallback = atIndex > 0 ? email.substring(0, atIndex) : "Blaie User";
        return fallback.length() > 100 ? fallback.substring(0, 100) : fallback;
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isBlank() ? null : trimmed;
    }

    private AuthUserResult toAuthUserResult(UserEntity user) {
        return new AuthUserResult(
                user.id(),
                user.username(),
                user.email(),
                authIdentityRepository.existsByUser_IdAndEmailVerifiedTrue(user.id()),
                authIdentityRepository.findByUser_IdAndProvider(user.id(), AuthConstants.PROVIDER_LOCAL)
                        .map(identity -> identity.passwordHash() != null && !identity.passwordHash().isBlank())
                        .orElse(false),
                user.displayName(),
                user.avatarUrl(),
                user.createdAt()
        );
    }

    private UserEntity requireCurrentActiveUser() {
        UUID userId = CurrentUserHolder.current()
                .map(CurrentUser::userId)
                .map(this::parseUserId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        return userRepository.findByIdAndStatus(userId, AuthConstants.USER_STATUS_ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
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

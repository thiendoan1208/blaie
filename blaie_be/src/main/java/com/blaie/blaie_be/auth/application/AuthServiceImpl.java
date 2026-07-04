package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
import com.blaie.blaie_be.auth.application.command.RegisterLocalCommand;
import com.blaie.blaie_be.auth.application.command.UpdatePasswordCommand;
import com.blaie.blaie_be.auth.application.command.UpdateUsernameCommand;
import com.blaie.blaie_be.auth.application.port.AuthIdentityStorePort;
import com.blaie.blaie_be.auth.application.port.AuthIdentityView;
import com.blaie.blaie_be.auth.application.port.AuthTokenPort;
import com.blaie.blaie_be.auth.application.port.AuthTokenSettingsPort;
import com.blaie.blaie_be.auth.application.port.AuthUserStorePort;
import com.blaie.blaie_be.auth.application.port.AuthUserView;
import com.blaie.blaie_be.auth.application.port.GoogleOAuthProfile;
import com.blaie.blaie_be.auth.application.port.PasswordHasherPort;
import com.blaie.blaie_be.auth.application.port.RefreshTokenStorePort;
import com.blaie.blaie_be.auth.application.port.RefreshTokenView;
import com.blaie.blaie_be.auth.application.result.AuthUserResult;
import com.blaie.blaie_be.auth.application.result.WebAuthResult;
import com.blaie.blaie_be.auth.domain.AuthConstants;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String DUMMY_PASSWORD = "BlaieDummyPasswordThatCannotAuthenticate";

    private final AuthUserStorePort userStore;
    private final AuthIdentityStorePort identityStore;
    private final RefreshTokenStorePort refreshTokenStore;
    private final PasswordHasherPort passwordHasher;
    private final String dummyPasswordHash;
    private final AuthTokenPort authToken;
    private final EmailVerificationService emailVerificationService;
    private final AuthTokenSettingsPort authSettings;
    private final Clock clock;

    public AuthServiceImpl(
            AuthUserStorePort userStore,
            AuthIdentityStorePort identityStore,
            RefreshTokenStorePort refreshTokenStore,
            PasswordHasherPort passwordHasher,
            AuthTokenPort authToken,
            EmailVerificationService emailVerificationService,
            AuthTokenSettingsPort authSettings,
            Clock clock
    ) {
        this.userStore = userStore;
        this.identityStore = identityStore;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordHasher = passwordHasher;
        this.dummyPasswordHash = passwordHasher.encode(DUMMY_PASSWORD);
        this.authToken = authToken;
        this.emailVerificationService = emailVerificationService;
        this.authSettings = authSettings;
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

        if (userStore.existsByUsernameNormalized(usernameNormalized)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userStore.existsByEmailNormalized(emailNormalized)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        AuthUserView user = userStore.createLocalUser(username, usernameNormalized, email, emailNormalized, displayName);
        identityStore.createLocalIdentity(user.id(), passwordHasher.encode(password));
        emailVerificationService.sendInitialVerification(user.id());
        return issueWebAuth(user, UUID.randomUUID(), userAgent);
    }

    @Transactional
    @Override
    public WebAuthResult loginLocal(LoginLocalCommand command, String userAgent) {
        String identifierNormalized = normalize(command.identifier());
        AuthIdentityView identity = findLocalIdentity(identifierNormalized).orElse(null);
        String passwordHash = identity == null ? dummyPasswordHash : identity.passwordHash();
        boolean passwordMatches = passwordHasher.matches(trim(command.password()), passwordHash);

        if (identity == null || !passwordMatches) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        
        AuthUserView user = identity.user();
        
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

        AuthUserView user = identityStore
                .findGoogleUserBySubject(subject)
                .orElseGet(() -> linkOrCreateGoogleUser(profile, subject, email, emailNormalized));

        if (!AuthConstants.USER_STATUS_ACTIVE.equals(user.status())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        return issueWebAuth(user, UUID.randomUUID(), userAgent);
    }

    @Transactional(noRollbackFor = AppException.class)
    @Override
    public WebAuthResult refreshWeb(String refreshToken, String userAgent) {
        String currentTokenHash = authToken.hashRefreshToken(refreshToken);
        RefreshTokenView currentToken = requireRefreshToken(currentTokenHash);
        Instant now = clock.instant();
        if (currentToken.revoked()) {
            refreshTokenStore.revokeFamily(currentToken.tokenFamilyId(), "family_breach", now);
            throw new AppException(ErrorCode.SESSION_REVOKED);
        }
        if (currentToken.isExpired(now)) {
            refreshTokenStore.revokeRefreshToken(currentTokenHash, "expired", now);
            throw new AppException(ErrorCode.SESSION_EXPIRED);
        }

        AuthUserView user = currentToken.user();
        if (!AuthConstants.USER_STATUS_ACTIVE.equals(user.status())) {
            refreshTokenStore.revokeAllUserTokens(user.id(), "user_not_active", now);
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        String nextRefreshTokenValue = authToken.generateRefreshToken();
        refreshTokenStore.rotateWebRefreshToken(
                currentTokenHash,
                authToken.hashRefreshToken(nextRefreshTokenValue),
                now.plus(authSettings.refreshTokenTtl()),
                userAgent,
                now
        );
        return new WebAuthResult(
                toAuthUserResult(user),
                authToken.issueAccessToken(user.id()),
                authSettings.accessTokenTtl(),
                nextRefreshTokenValue,
                authSettings.refreshTokenTtl()
        );
    }

    @Transactional
    @Override
    public void logoutWeb(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String tokenHash = authToken.hashRefreshToken(refreshToken);
        Optional<RefreshTokenView> tokenOpt = refreshTokenStore.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty() || tokenOpt.get().revoked()) {
            return;
        }
        refreshTokenStore.revokeRefreshToken(tokenHash, "logout", clock.instant());
    }

    @Transactional(readOnly = true)
    @Override
    public AuthUserResult currentUser() {
        return toAuthUserResult(requireCurrentActiveUser());
    }

    @Transactional
    @Override
    public AuthUserResult updateUsername(UpdateUsernameCommand command) {
        AuthUserView user = requireCurrentActiveUser();
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
        userStore.findByUsernameNormalized(usernameNormalized)
                .filter(existingUser -> !existingUser.id().equals(user.id()))
                .ifPresent(existingUser -> {
                    throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
                });
        userStore.updateUsername(user.id(), username, usernameNormalized);
        return toAuthUserResult(requireCurrentActiveUser());
    }

    @Transactional
    @Override
    public AuthUserResult updatePassword(UpdatePasswordCommand command) {
        AuthUserView user = requireCurrentActiveUser();
        String newPassword = trim(command.newPassword());
        Optional<AuthIdentityView> localIdentity = identityStore.findLocalIdentity(user.id());

        if (localIdentity.isPresent()) {
            String currentPassword = trim(command.currentPassword());
            if (currentPassword.isBlank()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Validation failed", Map.of(
                        "currentPassword", List.of("Current password is required")
                ));
            }
            AuthIdentityView identity = localIdentity.get();
            if (!passwordHasher.matches(currentPassword, identity.passwordHash())) {
                throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect", Map.of(
                        "currentPassword", List.of("Current password is incorrect")
                ));
            }
        }
        identityStore.updateLocalPasswordHash(user.id(), passwordHasher.encode(newPassword));

        refreshTokenStore.revokeAllUserTokens(user.id(), "password_changed", clock.instant());
        return toAuthUserResult(user);
    }
    
    private WebAuthResult issueWebAuth(AuthUserView user, UUID tokenFamilyId, String userAgent) {
        String refreshTokenValue = authToken.generateRefreshToken();
        refreshTokenStore.createWebRefreshToken(
                user.id(),
                authToken.hashRefreshToken(refreshTokenValue),
                tokenFamilyId,
                clock.instant().plus(authSettings.refreshTokenTtl()),
                userAgent
        );
        return new WebAuthResult(
                toAuthUserResult(user),
                authToken.issueAccessToken(user.id()),
                authSettings.accessTokenTtl(),
                refreshTokenValue,
                authSettings.refreshTokenTtl()
        );
    }

    private Optional<AuthIdentityView> findLocalIdentity(String identifierNormalized) {
        return identityStore.findSingleLocalIdentityByIdentifier(identifierNormalized);
    }

    private AuthUserView linkOrCreateGoogleUser(
            GoogleOAuthProfile profile,
            String subject,
            String email,
            String emailNormalized
    ) {
        AuthUserView user = userStore.findByEmailNormalized(emailNormalized)
                .orElseGet(() -> userStore.createGoogleUser(
                        email,
                        emailNormalized,
                        displayName(profile, email),
                        trimToNull(profile.avatarUrl())
                ));
        identityStore.createGoogleIdentity(user.id(), subject);
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

    private AuthUserResult toAuthUserResult(AuthUserView user) {
        return new AuthUserResult(
                user.id(),
                user.username(),
                user.email(),
                identityStore.existsVerifiedEmail(user.id()),
                identityStore.findLocalIdentity(user.id())
                        .map(identity -> identity.passwordHash() != null && !identity.passwordHash().isBlank())
                        .orElse(false),
                user.displayName(),
                user.avatarUrl(),
                user.createdAt()
        );
    }

    private AuthUserView requireCurrentActiveUser() {
        UUID userId = CurrentUserHolder.current()
                .map(CurrentUser::userId)
                .map(this::parseUserId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        return userStore.findActiveById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }

    private RefreshTokenView requireRefreshToken(String tokenHash) {
        return refreshTokenStore.findByTokenHash(tokenHash)
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

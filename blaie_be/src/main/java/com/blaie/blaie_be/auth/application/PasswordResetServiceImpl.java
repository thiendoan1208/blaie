package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.ConfirmPasswordResetCommand;
import com.blaie.blaie_be.auth.application.command.RequestPasswordResetCommand;
import com.blaie.blaie_be.auth.application.port.AuthActionTokenStorePort;
import com.blaie.blaie_be.auth.application.port.AuthActionTokenView;
import com.blaie.blaie_be.auth.application.port.AuthIdentityStorePort;
import com.blaie.blaie_be.auth.application.port.AuthTokenPort;
import com.blaie.blaie_be.auth.application.port.AuthUserStorePort;
import com.blaie.blaie_be.auth.application.port.AuthUserView;
import com.blaie.blaie_be.auth.application.port.EmailMessage;
import com.blaie.blaie_be.auth.application.port.EmailSenderPort;
import com.blaie.blaie_be.auth.application.port.EmailSettingsPort;
import com.blaie.blaie_be.auth.application.port.PasswordHasherPort;
import com.blaie.blaie_be.auth.application.port.RefreshTokenStorePort;
import com.blaie.blaie_be.auth.domain.AuthActionTokenType;
import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {
    private static final int CODE_BOUND = 1_000_000;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthUserStorePort userStore;
    private final AuthIdentityStorePort identityStore;
    private final AuthActionTokenStorePort actionTokenStore;
    private final RefreshTokenStorePort refreshTokenStore;
    private final AuthTokenPort authToken;
    private final PasswordHasherPort passwordHasher;
    private final EmailSenderPort emailSender;
    private final EmailSettingsPort emailSettings;
    private final Clock clock;

    public PasswordResetServiceImpl(
            AuthUserStorePort userStore,
            AuthIdentityStorePort identityStore,
            AuthActionTokenStorePort actionTokenStore,
            RefreshTokenStorePort refreshTokenStore,
            AuthTokenPort authToken,
            PasswordHasherPort passwordHasher,
            EmailSenderPort emailSender,
            EmailSettingsPort emailSettings,
            Clock clock
    ) {
        this.userStore = userStore;
        this.identityStore = identityStore;
        this.actionTokenStore = actionTokenStore;
        this.refreshTokenStore = refreshTokenStore;
        this.authToken = authToken;
        this.passwordHasher = passwordHasher;
        this.emailSender = emailSender;
        this.emailSettings = emailSettings;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void requestPasswordReset(RequestPasswordResetCommand command) {
        String emailNormalized = normalize(command.email());
        if (emailNormalized.isBlank()) {
            return;
        }

        userStore.findByEmailNormalized(emailNormalized)
                .filter(user -> AuthConstants.USER_STATUS_ACTIVE.equals(user.status()))
                .ifPresent(this::createAndSendResetCode);
    }

    @Transactional
    @Override
    public void confirmPasswordReset(ConfirmPasswordResetCommand command) {
        String emailNormalized = normalize(command.email());
        String code = trim(command.code());
        String newPassword = trim(command.newPassword());
        AuthUserView user = userStore.findByEmailNormalized(emailNormalized)
                .filter(candidate -> AuthConstants.USER_STATUS_ACTIVE.equals(candidate.status()))
                .orElseThrow(this::invalidCode);

        Instant now = clock.instant();
        AuthActionTokenView token = actionTokenStore
                .findLatestPendingForUpdate(
                        user.id(),
                        AuthActionTokenType.PASSWORD_RESET
                )
                .orElseThrow(this::invalidCode);

        if (token.isExpired(now)) {
            actionTokenStore.revokeToken(token.id(), "expired", now);
            throw new AppException(ErrorCode.PASSWORD_RESET_EXPIRED);
        }
        if (token.failedAttemptCount() >= MAX_FAILED_ATTEMPTS) {
            actionTokenStore.revokeToken(token.id(), "too_many_attempts", now);
            throw new AppException(ErrorCode.PASSWORD_RESET_TOO_MANY_ATTEMPTS);
        }
        if (!token.tokenHash().equals(hashResetCode(user, code))) {
            actionTokenStore.incrementFailedAttempt(token.id());
            if (token.failedAttemptCount() + 1 >= MAX_FAILED_ATTEMPTS) {
                actionTokenStore.revokeToken(token.id(), "too_many_attempts", now);
                throw new AppException(ErrorCode.PASSWORD_RESET_TOO_MANY_ATTEMPTS);
            }
            throw invalidCode();
        }

        identityStore.updateLocalPasswordHash(user.id(), passwordHasher.encode(newPassword));
        actionTokenStore.consumeToken(token.id(), now);
        actionTokenStore.revokeOpenTokens(user.id(), AuthActionTokenType.PASSWORD_RESET, "consumed", now);
        refreshTokenStore.revokeAllUserTokens(user.id(), "password_reset", now);
    }

    private void createAndSendResetCode(AuthUserView user) {
        Instant now = clock.instant();
        actionTokenStore.revokeOpenTokens(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                "replaced",
                now
        );
        String code = generateUniqueCode(user);
        actionTokenStore.createToken(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                hashResetCode(user, code),
                now.plus(emailSettings.passwordResetTtl())
        );
        emailSender.send(resetEmail(user, code));
    }

    private String generateUniqueCode(AuthUserView user) {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = "%06d".formatted(SECURE_RANDOM.nextInt(CODE_BOUND));
            if (!actionTokenStore.existsByTokenHash(hashResetCode(user, code))) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique password reset code");
    }

    private String hashResetCode(AuthUserView user, String code) {
        return authToken.hashOpaqueToken("%s:%s:%s".formatted(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                trim(code)
        ));
    }

    private EmailMessage resetEmail(AuthUserView user, String code) {
        String subject = "Reset your Blaie password";
        long minutes = emailSettings.passwordResetTtl().toMinutes();
        String text = """
                Hi %s,

                Use this code to reset your Blaie password:
                %s

                This code expires in %d minutes. If you did not request a password reset, you can ignore this email.
                """.formatted(user.displayName(), code, minutes);
        String html = """
                <div style="font-family:Arial,sans-serif;line-height:1.5;color:#171717">
                  <h1 style="font-size:20px;margin:0 0 12px">Reset your Blaie password</h1>
                  <p>Hi %s,</p>
                  <p>Use this code to reset your Blaie password.</p>
                  <p style="font-size:28px;font-weight:700;letter-spacing:6px;margin:18px 0">%s</p>
                  <p style="font-size:13px;color:#555">This code expires in %d minutes. If you did not request a password reset, you can ignore this email.</p>
                </div>
                """.formatted(escapeHtml(user.displayName()), code, minutes);
        return new EmailMessage(user.email(), subject, text, html);
    }

    private AppException invalidCode() {
        return new AppException(ErrorCode.PASSWORD_RESET_INVALID_CODE);
    }

    private String normalize(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

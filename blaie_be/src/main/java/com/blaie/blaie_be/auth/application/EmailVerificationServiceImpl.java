package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.port.EmailMessage;
import com.blaie.blaie_be.auth.application.port.AuthActionTokenStorePort;
import com.blaie.blaie_be.auth.application.port.AuthActionTokenView;
import com.blaie.blaie_be.auth.application.port.EmailSenderPort;
import com.blaie.blaie_be.auth.application.port.EmailSettingsPort;
import com.blaie.blaie_be.auth.application.port.AuthIdentityStorePort;
import com.blaie.blaie_be.auth.application.port.AuthTokenPort;
import com.blaie.blaie_be.auth.application.port.AuthUserStorePort;
import com.blaie.blaie_be.auth.application.port.AuthUserView;
import com.blaie.blaie_be.auth.domain.AuthActionTokenType;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.error.RateLimitedException;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private final AuthUserStorePort userStore;
    private final AuthIdentityStorePort identityStore;
    private final AuthActionTokenStorePort actionTokenStore;
    private final AuthTokenPort authToken;
    private final EmailSenderPort emailSender;
    private final EmailSettingsPort emailSettings;
    private final Clock clock;

    public EmailVerificationServiceImpl(
            AuthUserStorePort userStore,
            AuthIdentityStorePort identityStore,
            AuthActionTokenStorePort actionTokenStore,
            AuthTokenPort authToken,
            EmailSenderPort emailSender,
            EmailSettingsPort emailSettings,
            Clock clock
    ) {
        this.userStore = userStore;
        this.identityStore = identityStore;
        this.actionTokenStore = actionTokenStore;
        this.authToken = authToken;
        this.emailSender = emailSender;
        this.emailSettings = emailSettings;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void sendInitialVerification(UUID userId) {
        AuthUserView user = userStore.findActiveById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        createAndSendVerification(user, false);
    }

    @Transactional
    @Override
    public void resendVerificationForCurrentUser() {
        UUID userId = CurrentUserHolder.current()
                .map(CurrentUser::userId)
                .map(this::parseUserId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        AuthUserView user = userStore.findActiveById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        if (identityStore.existsVerifiedEmail(user.id())) {
            return;
        }
        enforceResendLimit(user);
        createAndSendVerification(user, true);
    }

    @Transactional
    @Override
    public void verifyEmailToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AppException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN);
        }

        Instant now = clock.instant();
        AuthActionTokenView token = actionTokenStore
                .findByTokenHashAndType(authToken.hashOpaqueToken(rawToken), AuthActionTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN));

        if (!token.isOpen(now)) {
            throw new AppException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN);
        }

        identityStore.markLocalEmailVerified(token.userId());
        actionTokenStore.consumeToken(token.id(), now);
    }

    @Override
    public String verifiedRedirectUrl() {
        return emailSettings.webBaseUrl() + "/verify-email/result?emailVerified=1";
    }

    @Override
    public String failedRedirectUrl() {
        return emailSettings.webBaseUrl() + "/verify-email/result?emailVerified=0";
    }

    private void createAndSendVerification(AuthUserView user, boolean revokePrevious) {
        Instant now = clock.instant();
        if (revokePrevious) {
            actionTokenStore.revokeOpenTokens(
                    user.id(),
                    AuthActionTokenType.EMAIL_VERIFICATION,
                    "replaced",
                    now
            );
        }
        String rawToken = authToken.generateOpaqueToken();
        actionTokenStore.createToken(
                user.id(),
                AuthActionTokenType.EMAIL_VERIFICATION,
                authToken.hashOpaqueToken(rawToken),
                now.plus(emailSettings.verificationTtl())
        );
        emailSender.send(verificationEmail(user, rawToken));
    }

    private void enforceResendLimit(AuthUserView user) {
        Instant now = clock.instant();
        Duration quotaWindow = emailSettings.verificationResendQuotaWindow();
        Instant windowStart = now.minus(quotaWindow);
        long recentSendCount = actionTokenStore.countByUserIdAndTypeSince(
                user.id(),
                AuthActionTokenType.EMAIL_VERIFICATION,
                windowStart
        );
        if (recentSendCount >= emailSettings.verificationResendQuotaLimit()) {
            Duration retryAfter = actionTokenStore
                    .findFirstCreatedAtByUserIdAndTypeSince(
                            user.id(),
                            AuthActionTokenType.EMAIL_VERIFICATION,
                            windowStart
                    )
                    .map(createdAt -> Duration.between(now, createdAt.plus(quotaWindow)))
                    .orElse(quotaWindow);
            throw new RateLimitedException(
                    ErrorCode.EMAIL_VERIFICATION_RATE_LIMITED,
                    "Email verification resend limit reached. Please try again later.",
                    retryAfter
            );
        }

        Duration cooldown = emailSettings.verificationResendCooldown();
        actionTokenStore.findLatestCreatedAtByUserIdAndType(user.id(), AuthActionTokenType.EMAIL_VERIFICATION)
                .filter(createdAt -> createdAt.plus(cooldown).isAfter(now))
                .ifPresent(createdAt -> {
                    throw new RateLimitedException(
                            ErrorCode.EMAIL_VERIFICATION_RATE_LIMITED,
                            "Please wait before requesting another verification email.",
                            Duration.between(now, createdAt.plus(cooldown))
                    );
                });
    }

    private EmailMessage verificationEmail(AuthUserView user, String rawToken) {
        String verifyUrl = emailSettings.apiBaseUrl()
                + "/auth/email/verify?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String subject = "Verify your Blaie email";
        String text = """
                Hi %s,

                Verify your email to start using Blaie:
                %s

                This link expires in 24 hours. If you did not create a Blaie account, you can ignore this email.
                """.formatted(user.displayName(), verifyUrl);
        String html = """
                <div style="font-family:Arial,sans-serif;line-height:1.5;color:#171717">
                  <h1 style="font-size:20px;margin:0 0 12px">Verify your Blaie email</h1>
                  <p>Hi %s,</p>
                  <p>Verify your email to start using Blaie.</p>
                  <p><a href="%s" style="display:inline-block;background:#6d5dfc;color:#fff;padding:10px 14px;border-radius:6px;text-decoration:none">Verify email</a></p>
                  <p style="font-size:13px;color:#555">This link expires in 24 hours. If you did not create a Blaie account, you can ignore this email.</p>
                </div>
                """.formatted(escapeHtml(user.displayName()), verifyUrl);
        return new EmailMessage(user.email(), subject, text, html);
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException exception) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

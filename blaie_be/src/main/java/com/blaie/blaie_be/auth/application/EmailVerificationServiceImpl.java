package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.port.EmailMessage;
import com.blaie.blaie_be.auth.application.port.EmailSenderPort;
import com.blaie.blaie_be.auth.domain.AuthActionTokenType;
import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.auth.infrastructure.email.EmailProperties;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthActionTokenEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthActionTokenRepository;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityRepository;
import com.blaie.blaie_be.auth.infrastructure.persistence.UserEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.UserRepository;
import com.blaie.blaie_be.auth.infrastructure.security.AuthTokenService;
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
    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final AuthTokenService authTokenService;
    private final EmailSenderPort emailSender;
    private final EmailProperties emailProperties;
    private final Clock clock;

    public EmailVerificationServiceImpl(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            AuthActionTokenRepository authActionTokenRepository,
            AuthTokenService authTokenService,
            EmailSenderPort emailSender,
            EmailProperties emailProperties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.authActionTokenRepository = authActionTokenRepository;
        this.authTokenService = authTokenService;
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void sendInitialVerification(UserEntity user) {
        createAndSendVerification(user, false);
    }

    @Transactional
    @Override
    public void resendVerificationForCurrentUser() {
        UUID userId = CurrentUserHolder.current()
                .map(CurrentUser::userId)
                .map(this::parseUserId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        UserEntity user = userRepository.findByIdAndStatus(userId, AuthConstants.USER_STATUS_ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        if (authIdentityRepository.existsByUser_IdAndEmailVerifiedTrue(user.id())) {
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
        AuthActionTokenEntity token = authActionTokenRepository
                .findByTokenHashAndType(authTokenService.hashOpaqueToken(rawToken), AuthActionTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN));

        if (!token.isOpen(now)) {
            throw new AppException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN);
        }

        AuthIdentityEntity localIdentity = authIdentityRepository
                .findByUser_IdAndProvider(token.user().id(), AuthConstants.PROVIDER_LOCAL)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN));

        localIdentity.markEmailVerified();
        token.consume(now);
    }

    @Override
    public String verifiedRedirectUrl() {
        return emailProperties.webBaseUrl() + "/verify-email/result?emailVerified=1";
    }

    @Override
    public String failedRedirectUrl() {
        return emailProperties.webBaseUrl() + "/verify-email/result?emailVerified=0";
    }

    private void createAndSendVerification(UserEntity user, boolean revokePrevious) {
        Instant now = clock.instant();
        if (revokePrevious) {
            authActionTokenRepository.revokeOpenTokens(
                    user.id(),
                    AuthActionTokenType.EMAIL_VERIFICATION,
                    "replaced",
                    now
            );
        }
        String rawToken = authTokenService.generateOpaqueToken();
        authActionTokenRepository.save(AuthActionTokenEntity.create(
                user,
                AuthActionTokenType.EMAIL_VERIFICATION,
                authTokenService.hashOpaqueToken(rawToken),
                now.plus(emailProperties.verificationTtl())
        ));
        emailSender.send(verificationEmail(user, rawToken));
    }

    private void enforceResendLimit(UserEntity user) {
        Instant now = clock.instant();
        Duration quotaWindow = emailProperties.verificationResendQuotaWindow();
        Instant windowStart = now.minus(quotaWindow);
        long recentSendCount = authActionTokenRepository.countByUser_IdAndTypeAndCreatedAtGreaterThanEqual(
                user.id(),
                AuthActionTokenType.EMAIL_VERIFICATION,
                windowStart
        );
        if (recentSendCount >= emailProperties.verificationResendQuotaLimit()) {
            Duration retryAfter = authActionTokenRepository
                    .findFirstByUser_IdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                            user.id(),
                            AuthActionTokenType.EMAIL_VERIFICATION,
                            windowStart
                    )
                    .map(token -> Duration.between(now, token.createdAt().plus(quotaWindow)))
                    .orElse(quotaWindow);
            throw new RateLimitedException(
                    ErrorCode.EMAIL_VERIFICATION_RATE_LIMITED,
                    "Email verification resend limit reached. Please try again later.",
                    retryAfter
            );
        }

        Duration cooldown = emailProperties.verificationResendCooldown();
        authActionTokenRepository.findTopByUser_IdAndTypeOrderByCreatedAtDesc(user.id(), AuthActionTokenType.EMAIL_VERIFICATION)
                .map(AuthActionTokenEntity::createdAt)
                .filter(createdAt -> createdAt.plus(cooldown).isAfter(now))
                .ifPresent(createdAt -> {
                    throw new RateLimitedException(
                            ErrorCode.EMAIL_VERIFICATION_RATE_LIMITED,
                            "Please wait before requesting another verification email.",
                            Duration.between(now, createdAt.plus(cooldown))
                    );
                });
    }

    private EmailMessage verificationEmail(UserEntity user, String rawToken) {
        String verifyUrl = emailProperties.apiBaseUrl()
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

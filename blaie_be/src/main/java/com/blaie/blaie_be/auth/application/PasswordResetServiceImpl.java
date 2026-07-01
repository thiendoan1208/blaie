package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.ConfirmPasswordResetCommand;
import com.blaie.blaie_be.auth.application.command.RequestPasswordResetCommand;
import com.blaie.blaie_be.auth.application.port.EmailMessage;
import com.blaie.blaie_be.auth.application.port.EmailSenderPort;
import com.blaie.blaie_be.auth.domain.AuthActionTokenType;
import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.auth.infrastructure.email.EmailProperties;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthActionTokenEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthActionTokenRepository;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityRepository;
import com.blaie.blaie_be.auth.infrastructure.persistence.RefreshTokenRepository;
import com.blaie.blaie_be.auth.infrastructure.persistence.UserEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.UserRepository;
import com.blaie.blaie_be.auth.infrastructure.security.AuthTokenService;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {
    private static final int CODE_BOUND = 1_000_000;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final AuthActionTokenRepository authActionTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenService authTokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmailSenderPort emailSender;
    private final EmailProperties emailProperties;
    private final Clock clock;

    public PasswordResetServiceImpl(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            AuthActionTokenRepository authActionTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuthTokenService authTokenService,
            PasswordEncoder passwordEncoder,
            EmailSenderPort emailSender,
            EmailProperties emailProperties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.authActionTokenRepository = authActionTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authTokenService = authTokenService;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.clock = clock;
    }

    @Transactional
    @Override
    public void requestPasswordReset(RequestPasswordResetCommand command) {
        String emailNormalized = normalize(command.email());
        if (emailNormalized.isBlank()) {
            return;
        }

        userRepository.findByEmailNormalized(emailNormalized)
                .filter(user -> AuthConstants.USER_STATUS_ACTIVE.equals(user.status()))
                .ifPresent(this::createAndSendResetCode);
    }

    @Transactional
    @Override
    public void confirmPasswordReset(ConfirmPasswordResetCommand command) {
        String emailNormalized = normalize(command.email());
        String code = trim(command.code());
        String newPassword = trim(command.newPassword());
        UserEntity user = userRepository.findByEmailNormalized(emailNormalized)
                .filter(candidate -> AuthConstants.USER_STATUS_ACTIVE.equals(candidate.status()))
                .orElseThrow(this::invalidCode);

        Instant now = clock.instant();
        AuthActionTokenEntity token = authActionTokenRepository
                .findLatestPendingForUpdate(
                        user.id(),
                        AuthActionTokenType.PASSWORD_RESET
                )
                .orElseThrow(this::invalidCode);

        if (token.isExpired(now)) {
            token.revoke("expired", now);
            throw new AppException(ErrorCode.PASSWORD_RESET_EXPIRED);
        }
        if (token.failedAttemptCount() >= MAX_FAILED_ATTEMPTS) {
            token.revoke("too_many_attempts", now);
            throw new AppException(ErrorCode.PASSWORD_RESET_TOO_MANY_ATTEMPTS);
        }
        if (!token.tokenHash().equals(hashResetCode(user, code))) {
            token.incrementFailedAttempt();
            if (token.failedAttemptCount() >= MAX_FAILED_ATTEMPTS) {
                token.revoke("too_many_attempts", now);
                throw new AppException(ErrorCode.PASSWORD_RESET_TOO_MANY_ATTEMPTS);
            }
            throw invalidCode();
        }

        String passwordHash = passwordEncoder.encode(newPassword);
        authIdentityRepository.findByUser_IdAndProvider(user.id(), AuthConstants.PROVIDER_LOCAL)
                .ifPresentOrElse(
                        identity -> identity.updatePasswordHash(passwordHash),
                        () -> authIdentityRepository.save(AuthIdentityEntity.local(user, passwordHash))
                );
        token.consume(now);
        authActionTokenRepository.revokeOpenTokens(user.id(), AuthActionTokenType.PASSWORD_RESET, "consumed", now);
        refreshTokenRepository.revokeAllUserTokens(user.id(), "password_reset", now);
    }

    private void createAndSendResetCode(UserEntity user) {
        Instant now = clock.instant();
        authActionTokenRepository.revokeOpenTokens(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                "replaced",
                now
        );
        String code = generateUniqueCode(user);
        authActionTokenRepository.save(AuthActionTokenEntity.create(
                user,
                AuthActionTokenType.PASSWORD_RESET,
                hashResetCode(user, code),
                now.plus(emailProperties.passwordResetTtl())
        ));
        emailSender.send(resetEmail(user, code));
    }

    private String generateUniqueCode(UserEntity user) {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = "%06d".formatted(SECURE_RANDOM.nextInt(CODE_BOUND));
            if (!authActionTokenRepository.existsByTokenHash(hashResetCode(user, code))) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique password reset code");
    }

    private String hashResetCode(UserEntity user, String code) {
        return authTokenService.hashOpaqueToken("%s:%s:%s".formatted(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                trim(code)
        ));
    }

    private EmailMessage resetEmail(UserEntity user, String code) {
        String subject = "Reset your Blaie password";
        long minutes = emailProperties.passwordResetTtl().toMinutes();
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

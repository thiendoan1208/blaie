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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-06-28T12:00:00Z");

    @Test
    void requestPasswordResetSendsSixDigitCodeWithoutLeakingMissingEmail() {
        Fixture fixture = new Fixture();
        UserEntity user = UserEntity.localUser(
                "mira",
                "mira",
                "mira@example.com",
                "mira@example.com",
                "Mira Stone"
        );
        when(fixture.userRepository.findByEmailNormalized("mira@example.com")).thenReturn(Optional.of(user));
        when(fixture.authTokenService.hashOpaqueToken(anyString())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        when(fixture.authActionTokenRepository.existsByTokenHash(anyString())).thenReturn(false);

        fixture.service.requestPasswordReset(new RequestPasswordResetCommand(" Mira@Example.com "));

        verify(fixture.authActionTokenRepository).revokeOpenTokens(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                "replaced",
                NOW
        );
        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(fixture.emailSender).send(emailCaptor.capture());
        Assertions.assertThat(emailCaptor.getValue().to()).isEqualTo("mira@example.com");
        Assertions.assertThat(emailCaptor.getValue().text()).containsPattern("\\b[0-9]{6}\\b");

        fixture.service.requestPasswordReset(new RequestPasswordResetCommand("missing@example.com"));
    }

    @Test
    void confirmPasswordResetUpdatesPasswordAndRevokesRefreshTokens() {
        Fixture fixture = new Fixture();
        UserEntity user = UserEntity.localUser(
                "mira",
                "mira",
                "mira@example.com",
                "mira@example.com",
                "Mira Stone"
        );
        AuthIdentityEntity identity = AuthIdentityEntity.local(user, "old-password-hash");
        AuthActionTokenEntity token = AuthActionTokenEntity.create(
                user,
                AuthActionTokenType.PASSWORD_RESET,
                "reset-code-hash",
                NOW.plus(Duration.ofMinutes(15))
        );
        when(fixture.userRepository.findByEmailNormalized("mira@example.com")).thenReturn(Optional.of(user));
        when(fixture.authActionTokenRepository
                .findLatestPendingForUpdate(
                        user.id(),
                        AuthActionTokenType.PASSWORD_RESET
                ))
                .thenReturn(Optional.of(token));
        when(fixture.authTokenService.hashOpaqueToken("%s:%s:%s".formatted(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                "123456"
        ))).thenReturn("reset-code-hash");
        when(fixture.passwordEncoder.encode("Password2@")).thenReturn("new-password-hash");
        when(fixture.authIdentityRepository.findByUser_IdAndProvider(user.id(), AuthConstants.PROVIDER_LOCAL))
                .thenReturn(Optional.of(identity));

        fixture.service.confirmPasswordReset(new ConfirmPasswordResetCommand(
                "mira@example.com",
                "123456",
                "Password2@"
        ));

        Assertions.assertThat(identity.passwordHash()).isEqualTo("new-password-hash");
        verify(fixture.refreshTokenRepository).revokeAllUserTokens(user.id(), "password_reset", NOW);
        verify(fixture.authActionTokenRepository).revokeOpenTokens(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                "consumed",
                NOW
        );
    }

    @Test
    void confirmPasswordResetCreatesLocalIdentityForGoogleOnlyUser() {
        Fixture fixture = new Fixture();
        UserEntity user = UserEntity.googleUser(
                "google@example.com",
                "google@example.com",
                "Google User",
                null
        );
        AuthActionTokenEntity token = AuthActionTokenEntity.create(
                user,
                AuthActionTokenType.PASSWORD_RESET,
                "reset-code-hash",
                NOW.plus(Duration.ofMinutes(15))
        );
        when(fixture.userRepository.findByEmailNormalized("google@example.com")).thenReturn(Optional.of(user));
        when(fixture.authActionTokenRepository
                .findLatestPendingForUpdate(
                        user.id(),
                        AuthActionTokenType.PASSWORD_RESET
                ))
                .thenReturn(Optional.of(token));
        when(fixture.authTokenService.hashOpaqueToken("%s:%s:%s".formatted(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                "123456"
        ))).thenReturn("reset-code-hash");
        when(fixture.passwordEncoder.encode("Password2@")).thenReturn("new-password-hash");
        when(fixture.authIdentityRepository.findByUser_IdAndProvider(user.id(), AuthConstants.PROVIDER_LOCAL))
                .thenReturn(Optional.empty());

        fixture.service.confirmPasswordReset(new ConfirmPasswordResetCommand(
                "google@example.com",
                "123456",
                "Password2@"
        ));

        ArgumentCaptor<AuthIdentityEntity> identityCaptor = ArgumentCaptor.forClass(AuthIdentityEntity.class);
        verify(fixture.authIdentityRepository).save(identityCaptor.capture());
        Assertions.assertThat(identityCaptor.getValue().provider()).isEqualTo(AuthConstants.PROVIDER_LOCAL);
        Assertions.assertThat(identityCaptor.getValue().passwordHash()).isEqualTo("new-password-hash");
        verify(fixture.refreshTokenRepository).revokeAllUserTokens(user.id(), "password_reset", NOW);
    }

    @Test
    void confirmPasswordResetRejectsExpiredOrWrongCodes() {
        Fixture fixture = new Fixture();
        UserEntity user = UserEntity.localUser(
                "mira",
                "mira",
                "mira@example.com",
                "mira@example.com",
                "Mira Stone"
        );
        AuthActionTokenEntity expiredToken = AuthActionTokenEntity.create(
                user,
                AuthActionTokenType.PASSWORD_RESET,
                "reset-code-hash",
                NOW.minusSeconds(1)
        );
        when(fixture.userRepository.findByEmailNormalized("mira@example.com")).thenReturn(Optional.of(user));
        when(fixture.authActionTokenRepository
                .findLatestPendingForUpdate(
                        user.id(),
                        AuthActionTokenType.PASSWORD_RESET
                ))
                .thenReturn(Optional.of(expiredToken));

        Assertions.assertThatThrownBy(() -> fixture.service.confirmPasswordReset(new ConfirmPasswordResetCommand(
                        "mira@example.com",
                        "123456",
                        "Password2@"
                )))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.PASSWORD_RESET_EXPIRED));

        AuthActionTokenEntity token = AuthActionTokenEntity.create(
                user,
                AuthActionTokenType.PASSWORD_RESET,
                "reset-code-hash",
                NOW.plus(Duration.ofMinutes(15))
        );
        when(fixture.authActionTokenRepository
                .findLatestPendingForUpdate(
                        user.id(),
                        AuthActionTokenType.PASSWORD_RESET
                ))
                .thenReturn(Optional.of(token));
        when(fixture.authTokenService.hashOpaqueToken(anyString())).thenReturn("different-hash");

        Assertions.assertThatThrownBy(() -> fixture.service.confirmPasswordReset(new ConfirmPasswordResetCommand(
                        "mira@example.com",
                        "999999",
                        "Password2@"
                )))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.PASSWORD_RESET_INVALID_CODE));
        Assertions.assertThat(token.failedAttemptCount()).isEqualTo(1);
    }

    private static class Fixture {
        private final UserRepository userRepository = mock(UserRepository.class);
        private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
        private final AuthActionTokenRepository authActionTokenRepository = mock(AuthActionTokenRepository.class);
        private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        private final AuthTokenService authTokenService = mock(AuthTokenService.class);
        private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        private final EmailSenderPort emailSender = mock(EmailSenderPort.class);
        private final EmailProperties emailProperties = mock(EmailProperties.class);
        private final PasswordResetServiceImpl service;

        private Fixture() {
            when(emailProperties.passwordResetTtl()).thenReturn(Duration.ofMinutes(15));
            service = new PasswordResetServiceImpl(
                    userRepository,
                    authIdentityRepository,
                    authActionTokenRepository,
                    refreshTokenRepository,
                    authTokenService,
                    passwordEncoder,
                    emailSender,
                    emailProperties,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );
        }
    }
}

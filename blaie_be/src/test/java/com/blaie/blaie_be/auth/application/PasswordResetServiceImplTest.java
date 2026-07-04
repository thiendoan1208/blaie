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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-06-28T12:00:00Z");

    @Test
    void requestPasswordResetSendsSixDigitCodeWithoutLeakingMissingEmail() {
        Fixture fixture = new Fixture();
        AuthUserView user = user("mira", "mira@example.com");
        when(fixture.userStore.findByEmailNormalized("mira@example.com")).thenReturn(Optional.of(user));
        when(fixture.authToken.hashOpaqueToken(anyString())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        when(fixture.actionTokenStore.existsByTokenHash(anyString())).thenReturn(false);

        fixture.service.requestPasswordReset(new RequestPasswordResetCommand(" Mira@Example.com "));

        verify(fixture.actionTokenStore).revokeOpenTokens(
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
        AuthUserView user = user("mira", "mira@example.com");
        AuthActionTokenView token = token(user.id(), "reset-code-hash", NOW.plus(Duration.ofMinutes(15)), 0);
        when(fixture.userStore.findByEmailNormalized("mira@example.com")).thenReturn(Optional.of(user));
        when(fixture.actionTokenStore.findLatestPendingForUpdate(user.id(), AuthActionTokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(fixture.authToken.hashOpaqueToken("%s:%s:%s".formatted(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                "123456"
        ))).thenReturn("reset-code-hash");
        when(fixture.passwordHasher.encode("Password2@")).thenReturn("new-password-hash");

        fixture.service.confirmPasswordReset(new ConfirmPasswordResetCommand(
                "mira@example.com",
                "123456",
                "Password2@"
        ));

        verify(fixture.identityStore).updateLocalPasswordHash(user.id(), "new-password-hash");
        verify(fixture.refreshTokenStore).revokeAllUserTokens(user.id(), "password_reset", NOW);
        verify(fixture.actionTokenStore).consumeToken(token.id(), NOW);
        verify(fixture.actionTokenStore).revokeOpenTokens(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                "consumed",
                NOW
        );
    }

    @Test
    void confirmPasswordResetCreatesLocalIdentityForGoogleOnlyUser() {
        Fixture fixture = new Fixture();
        AuthUserView user = user(null, "google@example.com");
        AuthActionTokenView token = token(user.id(), "reset-code-hash", NOW.plus(Duration.ofMinutes(15)), 0);
        when(fixture.userStore.findByEmailNormalized("google@example.com")).thenReturn(Optional.of(user));
        when(fixture.actionTokenStore.findLatestPendingForUpdate(user.id(), AuthActionTokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(fixture.authToken.hashOpaqueToken("%s:%s:%s".formatted(
                user.id(),
                AuthActionTokenType.PASSWORD_RESET,
                "123456"
        ))).thenReturn("reset-code-hash");
        when(fixture.passwordHasher.encode("Password2@")).thenReturn("new-password-hash");

        fixture.service.confirmPasswordReset(new ConfirmPasswordResetCommand(
                "google@example.com",
                "123456",
                "Password2@"
        ));

        verify(fixture.identityStore).updateLocalPasswordHash(user.id(), "new-password-hash");
        verify(fixture.refreshTokenStore).revokeAllUserTokens(user.id(), "password_reset", NOW);
    }

    @Test
    void confirmPasswordResetRejectsExpiredOrWrongCodes() {
        Fixture fixture = new Fixture();
        AuthUserView user = user("mira", "mira@example.com");
        AuthActionTokenView expiredToken = token(user.id(), "reset-code-hash", NOW.minusSeconds(1), 0);
        when(fixture.userStore.findByEmailNormalized("mira@example.com")).thenReturn(Optional.of(user));
        when(fixture.actionTokenStore.findLatestPendingForUpdate(user.id(), AuthActionTokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(expiredToken));

        Assertions.assertThatThrownBy(() -> fixture.service.confirmPasswordReset(new ConfirmPasswordResetCommand(
                        "mira@example.com",
                        "123456",
                        "Password2@"
                )))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.PASSWORD_RESET_EXPIRED));

        AuthActionTokenView token = token(user.id(), "reset-code-hash", NOW.plus(Duration.ofMinutes(15)), 0);
        when(fixture.actionTokenStore.findLatestPendingForUpdate(user.id(), AuthActionTokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(fixture.authToken.hashOpaqueToken(anyString())).thenReturn("different-hash");

        Assertions.assertThatThrownBy(() -> fixture.service.confirmPasswordReset(new ConfirmPasswordResetCommand(
                        "mira@example.com",
                        "999999",
                        "Password2@"
                )))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.PASSWORD_RESET_INVALID_CODE));
        verify(fixture.actionTokenStore).incrementFailedAttempt(token.id());
    }

    private static AuthUserView user(String username, String email) {
        return new AuthUserView(
                UUID.randomUUID(),
                username,
                email,
                AuthConstants.USER_STATUS_ACTIVE,
                false,
                username == null ? "Google User" : username,
                null,
                NOW
        );
    }

    private static AuthActionTokenView token(UUID userId, String hash, Instant expiresAt, int failedAttempts) {
        return new AuthActionTokenView(
                UUID.randomUUID(),
                userId,
                hash,
                expiresAt,
                null,
                null,
                failedAttempts,
                NOW
        );
    }

    private static class Fixture {
        private final AuthUserStorePort userStore = mock(AuthUserStorePort.class);
        private final AuthIdentityStorePort identityStore = mock(AuthIdentityStorePort.class);
        private final AuthActionTokenStorePort actionTokenStore = mock(AuthActionTokenStorePort.class);
        private final RefreshTokenStorePort refreshTokenStore = mock(RefreshTokenStorePort.class);
        private final AuthTokenPort authToken = mock(AuthTokenPort.class);
        private final PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);
        private final EmailSenderPort emailSender = mock(EmailSenderPort.class);
        private final EmailSettingsPort emailSettings = mock(EmailSettingsPort.class);
        private final PasswordResetServiceImpl service;

        private Fixture() {
            when(emailSettings.passwordResetTtl()).thenReturn(Duration.ofMinutes(15));
            service = new PasswordResetServiceImpl(
                    userStore,
                    identityStore,
                    actionTokenStore,
                    refreshTokenStore,
                    authToken,
                    passwordHasher,
                    emailSender,
                    emailSettings,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );
        }
    }
}

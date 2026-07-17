package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
import com.blaie.blaie_be.auth.application.command.UpdatePasswordCommand;
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
import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-06-20T12:00:00Z");

    @Test
    void missingIdentityStillRunsPasswordVerification() {
        Fixture fixture = new Fixture();
        when(fixture.identityStore.findSingleLocalIdentityByIdentifier("missing@example.com"))
                .thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> fixture.service.loginLocal(
                        new LoginLocalCommand(" Missing@Example.com ", " Password1! "),
                        "test-agent"
                ))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        verify(fixture.passwordHasher).matches("Password1!", "dummy-password-hash");
    }

    @Test
    void logoutUsesInjectedClockForTokenMutation() {
        Fixture fixture = new Fixture();
        AuthUserView user = user("mira", "mira@example.com");
        UUID familyId = UUID.randomUUID();
        when(fixture.authToken.hashRefreshToken("refresh-token")).thenReturn("refresh-token-hash");
        when(fixture.refreshTokenStore.findByTokenHash("refresh-token-hash"))
                .thenReturn(Optional.of(new RefreshTokenView(user, familyId, NOW.plus(Duration.ofDays(1)), false)));

        fixture.service.logoutWeb("refresh-token");

        verify(fixture.refreshTokenStore).revokeRefreshToken("refresh-token-hash", "logout", NOW);
    }

    @Test
    void googleLoginRejectsUnverifiedEmail() {
        Fixture fixture = new Fixture();

        Assertions.assertThatThrownBy(() -> fixture.service.loginGoogle(
                        new GoogleOAuthProfile("google-sub", "user@example.com", false, "User Example", null),
                        "test-agent"
                ))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED));
    }

    @Test
    void googleLoginLinksExistingUserByVerifiedEmail() {
        Fixture fixture = new Fixture();
        AuthUserView existingUser = user("alex", "alex@example.com");
        when(fixture.identityStore.findGoogleUserBySubject("google-sub-123")).thenReturn(Optional.empty());
        when(fixture.userStore.findByEmailNormalized("alex@example.com")).thenReturn(Optional.of(existingUser));

        fixture.service.loginGoogle(
                new GoogleOAuthProfile("google-sub-123", " Alex@Example.com ", true, "Alex Google", "https://example.com/avatar.png"),
                "test-agent"
        );

        verify(fixture.userStore, never()).createGoogleUser(anyString(), anyString(), anyString(), any());
        verify(fixture.identityStore).createGoogleIdentity(existingUser.id(), "google-sub-123");
    }

    @Test
    void googleLoginCreatesUserWhenEmailIsNew() {
        Fixture fixture = new Fixture();
        AuthUserView newUser = user(null, "new@example.com");
        when(fixture.identityStore.findGoogleUserBySubject("google-sub-456")).thenReturn(Optional.empty());
        when(fixture.userStore.findByEmailNormalized("new@example.com")).thenReturn(Optional.empty());
        when(fixture.userStore.createGoogleUser(
                "new@example.com",
                "new@example.com",
                "New User",
                "https://example.com/new.png"
        )).thenReturn(newUser);

        fixture.service.loginGoogle(
                new GoogleOAuthProfile("google-sub-456", "new@example.com", true, "New User", "https://example.com/new.png"),
                "test-agent"
        );

        verify(fixture.userStore).createGoogleUser(
                "new@example.com",
                "new@example.com",
                "New User",
                "https://example.com/new.png"
        );
        verify(fixture.identityStore).createGoogleIdentity(newUser.id(), "google-sub-456");
    }

    @Test
    void updatePasswordCreatesLocalIdentityForGoogleOnlyUser() {
        Fixture fixture = new Fixture();
        AuthUserView googleUser = user(null, "google@example.com");
        AuthIdentityView savedLocalIdentity = new AuthIdentityView(
                googleUser,
                AuthConstants.PROVIDER_LOCAL,
                googleUser.email(),
                true,
                "encoded-new-password"
        );
        when(fixture.userStore.findActiveById(googleUser.id())).thenReturn(Optional.of(googleUser));
        when(fixture.identityStore.findLocalIdentity(googleUser.id()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedLocalIdentity));
        when(fixture.passwordHasher.encode("Newpass1!")).thenReturn("encoded-new-password");
        when(fixture.identityStore.existsVerifiedEmail(googleUser.id())).thenReturn(true);

        var result = CurrentUserHolder.runAs(
                new CurrentUser(googleUser.id().toString(), false, Set.of()),
                () -> fixture.service.updatePassword(new UpdatePasswordCommand(null, " Newpass1! "))
        );

        verify(fixture.identityStore).updateLocalPasswordHash(googleUser.id(), "encoded-new-password");
        Assertions.assertThat(result.username()).isNull();
        Assertions.assertThat(result.hasPassword()).isTrue();
        verify(fixture.passwordHasher, never()).matches(any(), anyString());
        verify(fixture.refreshTokenStore).revokeAllUserTokens(googleUser.id(), "password_changed", NOW);
    }

    @Test
    void updatePasswordChangesLocalPasswordAndRevokesRefreshTokens() {
        Fixture fixture = new Fixture();
        AuthUserView user = user("mira", "mira@example.com");
        AuthIdentityView identity = new AuthIdentityView(
                user,
                AuthConstants.PROVIDER_LOCAL,
                user.email(),
                true,
                "old-password-hash"
        );
        when(fixture.userStore.findActiveById(user.id())).thenReturn(Optional.of(user));
        when(fixture.identityStore.findLocalIdentity(user.id())).thenReturn(Optional.of(identity));
        when(fixture.identityStore.existsVerifiedEmail(user.id())).thenReturn(true);
        when(fixture.passwordHasher.matches("Password1!", "old-password-hash")).thenReturn(true);
        when(fixture.passwordHasher.encode("Password2@")).thenReturn("new-password-hash");

        var result = CurrentUserHolder.runAs(
                new CurrentUser(user.id().toString(), false, Set.of()),
                () -> fixture.service.updatePassword(new UpdatePasswordCommand(" Password1! ", " Password2@ "))
        );

        verify(fixture.identityStore).updateLocalPasswordHash(user.id(), "new-password-hash");
        Assertions.assertThat(result.hasPassword()).isTrue();
        verify(fixture.refreshTokenStore).revokeAllUserTokens(user.id(), "password_changed", NOW);
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

    private static class Fixture {
        private final AuthUserStorePort userStore = mock(AuthUserStorePort.class);
        private final AuthIdentityStorePort identityStore = mock(AuthIdentityStorePort.class);
        private final RefreshTokenStorePort refreshTokenStore = mock(RefreshTokenStorePort.class);
        private final PasswordHasherPort passwordHasher = mock(PasswordHasherPort.class);
        private final AuthTokenPort authToken = mock(AuthTokenPort.class);
        private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        private final AuthTokenSettingsPort authSettings = mock(AuthTokenSettingsPort.class);
        private final AuthServiceImpl service;

        private Fixture() {
            when(passwordHasher.encode("BlaieDummyPasswordThatCannotAuthenticate")).thenReturn("dummy-password-hash");
            when(authSettings.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
            when(authSettings.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
            when(authToken.generateRefreshToken()).thenReturn("refresh-token");
            when(authToken.hashRefreshToken("refresh-token")).thenReturn("refresh-token-hash");
            when(authToken.issueAccessToken(any())).thenReturn("access-token");
            service = new AuthServiceImpl(
                    userStore,
                    identityStore,
                    refreshTokenStore,
                    passwordHasher,
                    authToken,
                    emailVerificationService,
                    authSettings,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );
        }
    }
}

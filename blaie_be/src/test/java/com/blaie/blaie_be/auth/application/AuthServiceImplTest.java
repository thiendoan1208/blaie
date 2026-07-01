package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
import com.blaie.blaie_be.auth.application.command.UpdatePasswordCommand;
import com.blaie.blaie_be.auth.application.port.GoogleOAuthProfile;
import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityRepository;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.ArgumentCaptor;

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
        UserRepository userRepository = mock(UserRepository.class);
        AuthIdentityRepository identityRepository = mock(AuthIdentityRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        AuthProperties properties = new AuthProperties();
        when(passwordEncoder.encode("BlaieDummyPasswordThatCannotAuthenticate")).thenReturn("dummy-password-hash");
        when(identityRepository.findAllByProviderAndIdentifier("local", "missing@example.com"))
                .thenReturn(List.of());

        AuthServiceImpl service = new AuthServiceImpl(
                userRepository,
                identityRepository,
                refreshTokenRepository,
                passwordEncoder,
                tokenService,
                emailVerificationService,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        Assertions.assertThatThrownBy(() -> service.loginLocal(
                        new LoginLocalCommand(" Missing@Example.com ", " Password1! "),
                        "test-agent"
                ))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        verify(passwordEncoder).matches("Password1!", "dummy-password-hash");
    }

    @Test
    void logoutUsesInjectedClockForTokenMutation() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthIdentityRepository identityRepository = mock(AuthIdentityRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        RefreshTokenEntity refreshToken = mock(RefreshTokenEntity.class);
        when(passwordEncoder.encode("BlaieDummyPasswordThatCannotAuthenticate")).thenReturn("dummy-password-hash");
        when(tokenService.hashRefreshToken("refresh-token")).thenReturn("refresh-token-hash");
        when(refreshTokenRepository.findByTokenHash("refresh-token-hash")).thenReturn(Optional.of(refreshToken));

        AuthServiceImpl service = new AuthServiceImpl(
                userRepository,
                identityRepository,
                refreshTokenRepository,
                passwordEncoder,
                tokenService,
                emailVerificationService,
                new AuthProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.logoutWeb("refresh-token");

        verify(refreshToken).markUsed(NOW);
        verify(refreshToken).revoke("logout", null, NOW);
    }

    @Test
    void googleLoginRejectsUnverifiedEmail() {
        AuthServiceImpl service = serviceWithMocks(
                mock(UserRepository.class),
                mock(AuthIdentityRepository.class),
                mock(RefreshTokenRepository.class),
                mock(PasswordEncoder.class),
                mock(AuthTokenService.class),
                mock(EmailVerificationService.class)
        );

        Assertions.assertThatThrownBy(() -> service.loginGoogle(
                        new GoogleOAuthProfile("google-sub", "user@example.com", false, "User Example", null),
                        "test-agent"
                ))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED));
    }

    @Test
    void googleLoginLinksExistingUserByVerifiedEmail() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthIdentityRepository identityRepository = mock(AuthIdentityRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        UserEntity existingUser = UserEntity.localUser(
                "alex",
                "alex",
                "alex@example.com",
                "alex@example.com",
                "Alex Example"
        );
        when(passwordEncoder.encode("BlaieDummyPasswordThatCannotAuthenticate")).thenReturn("dummy-password-hash");
        when(identityRepository.findByProviderAndProviderSubject(AuthConstants.PROVIDER_GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailNormalized("alex@example.com")).thenReturn(Optional.of(existingUser));
        when(tokenService.generateRefreshToken()).thenReturn("refresh-token");
        when(tokenService.hashRefreshToken("refresh-token")).thenReturn("refresh-token-hash");
        when(tokenService.issueAccessToken(existingUser.id())).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthServiceImpl service = serviceWithMocks(
                userRepository,
                identityRepository,
                refreshTokenRepository,
                passwordEncoder,
                tokenService,
                emailVerificationService
        );

        service.loginGoogle(
                new GoogleOAuthProfile("google-sub-123", " Alex@Example.com ", true, "Alex Google", "https://example.com/avatar.png"),
                "test-agent"
        );

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        verify(identityRepository).saveAndFlush(any(AuthIdentityEntity.class));
    }

    @Test
    void googleLoginCreatesUserWhenEmailIsNew() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthIdentityRepository identityRepository = mock(AuthIdentityRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        when(passwordEncoder.encode("BlaieDummyPasswordThatCannotAuthenticate")).thenReturn("dummy-password-hash");
        when(identityRepository.findByProviderAndProviderSubject(AuthConstants.PROVIDER_GOOGLE, "google-sub-456"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailNormalized("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenService.generateRefreshToken()).thenReturn("refresh-token");
        when(tokenService.hashRefreshToken("refresh-token")).thenReturn("refresh-token-hash");
        when(tokenService.issueAccessToken(any())).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthServiceImpl service = serviceWithMocks(
                userRepository,
                identityRepository,
                refreshTokenRepository,
                passwordEncoder,
                tokenService,
                emailVerificationService
        );

        service.loginGoogle(
                new GoogleOAuthProfile("google-sub-456", "new@example.com", true, "New User", "https://example.com/new.png"),
                "test-agent"
        );

        verify(userRepository).saveAndFlush(any(UserEntity.class));
        verify(identityRepository).saveAndFlush(any(AuthIdentityEntity.class));
    }

    @Test
    void updatePasswordCreatesLocalIdentityForGoogleOnlyUser() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthIdentityRepository identityRepository = mock(AuthIdentityRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        UserEntity googleUser = UserEntity.googleUser(
                "google@example.com",
                "google@example.com",
                "Google User",
                "https://example.com/avatar.png"
        );
        AuthIdentityEntity savedLocalIdentity = AuthIdentityEntity.local(googleUser, "encoded-new-password");
        when(passwordEncoder.encode("BlaieDummyPasswordThatCannotAuthenticate")).thenReturn("dummy-password-hash");
        when(passwordEncoder.encode("Newpass1!")).thenReturn("encoded-new-password");
        when(userRepository.findByIdAndStatus(googleUser.id(), AuthConstants.USER_STATUS_ACTIVE))
                .thenReturn(Optional.of(googleUser));
        when(identityRepository.findByUser_IdAndProvider(googleUser.id(), AuthConstants.PROVIDER_LOCAL))
                .thenReturn(Optional.empty(), Optional.of(savedLocalIdentity));
        when(identityRepository.existsByUser_IdAndEmailVerifiedTrue(googleUser.id())).thenReturn(true);

        AuthServiceImpl service = serviceWithMocks(
                userRepository,
                identityRepository,
                refreshTokenRepository,
                passwordEncoder,
                tokenService,
                emailVerificationService
        );

        var result = CurrentUserHolder.runAs(
                new CurrentUser(googleUser.id().toString(), null, false, Set.of()),
                () -> service.updatePassword(new UpdatePasswordCommand(null, " Newpass1! "))
        );

        ArgumentCaptor<AuthIdentityEntity> identityCaptor = ArgumentCaptor.forClass(AuthIdentityEntity.class);
        verify(identityRepository).save(identityCaptor.capture());
        Assertions.assertThat(identityCaptor.getValue().provider()).isEqualTo(AuthConstants.PROVIDER_LOCAL);
        Assertions.assertThat(identityCaptor.getValue().passwordHash()).isEqualTo("encoded-new-password");
        Assertions.assertThat(result.username()).isNull();
        Assertions.assertThat(result.hasPassword()).isTrue();
        verify(passwordEncoder, never()).matches(any(), anyString());
        verify(refreshTokenRepository).revokeAllUserTokens(googleUser.id(), "password_changed", NOW);
    }

    @Test
    void updatePasswordChangesLocalPasswordAndRevokesRefreshTokens() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthIdentityRepository identityRepository = mock(AuthIdentityRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        UserEntity user = UserEntity.localUser(
                "mira",
                "mira",
                "mira@example.com",
                "mira@example.com",
                "Mira Stone"
        );
        AuthIdentityEntity identity = AuthIdentityEntity.local(user, "old-password-hash");
        when(passwordEncoder.encode("BlaieDummyPasswordThatCannotAuthenticate")).thenReturn("dummy-password-hash");
        when(passwordEncoder.matches("Password1!", "old-password-hash")).thenReturn(true);
        when(passwordEncoder.encode("Password2@")).thenReturn("new-password-hash");
        when(userRepository.findByIdAndStatus(user.id(), AuthConstants.USER_STATUS_ACTIVE))
                .thenReturn(Optional.of(user));
        when(identityRepository.findByUser_IdAndProvider(user.id(), AuthConstants.PROVIDER_LOCAL))
                .thenReturn(Optional.of(identity));
        when(identityRepository.existsByUser_IdAndEmailVerifiedTrue(user.id())).thenReturn(true);

        AuthServiceImpl service = serviceWithMocks(
                userRepository,
                identityRepository,
                refreshTokenRepository,
                passwordEncoder,
                tokenService,
                emailVerificationService
        );

        var result = CurrentUserHolder.runAs(
                new CurrentUser(user.id().toString(), user.username(), false, Set.of()),
                () -> service.updatePassword(new UpdatePasswordCommand(" Password1! ", " Password2@ "))
        );

        Assertions.assertThat(identity.passwordHash()).isEqualTo("new-password-hash");
        Assertions.assertThat(result.hasPassword()).isTrue();
        verify(refreshTokenRepository).revokeAllUserTokens(user.id(), "password_changed", NOW);
    }

    private AuthServiceImpl serviceWithMocks(
            UserRepository userRepository,
            AuthIdentityRepository identityRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService tokenService,
            EmailVerificationService emailVerificationService
    ) {
        when(passwordEncoder.encode("BlaieDummyPasswordThatCannotAuthenticate")).thenReturn("dummy-password-hash");
        return new AuthServiceImpl(
                userRepository,
                identityRepository,
                refreshTokenRepository,
                passwordEncoder,
                tokenService,
                emailVerificationService,
                new AuthProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}

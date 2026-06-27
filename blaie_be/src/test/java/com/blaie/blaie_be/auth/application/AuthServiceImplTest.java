package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
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

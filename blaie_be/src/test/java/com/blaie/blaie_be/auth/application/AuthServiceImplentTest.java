package com.blaie.blaie_be.auth.application;

import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityRepository;
import com.blaie.blaie_be.auth.infrastructure.persistence.RefreshTokenRepository;
import com.blaie.blaie_be.auth.infrastructure.persistence.UserRepository;
import com.blaie.blaie_be.auth.infrastructure.security.AuthProperties;
import com.blaie.blaie_be.auth.infrastructure.security.AuthTokenService;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplentTest {
    @Test
    void missingIdentityStillRunsPasswordVerification() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthIdentityRepository identityRepository = mock(AuthIdentityRepository.class);
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
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
                properties
        );

        Assertions.assertThatThrownBy(() -> service.loginLocal(
                        new LoginLocalCommand(" Missing@Example.com ", "password123"),
                        "test-agent"
                ))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        verify(passwordEncoder).matches("password123", "dummy-password-hash");
    }
}

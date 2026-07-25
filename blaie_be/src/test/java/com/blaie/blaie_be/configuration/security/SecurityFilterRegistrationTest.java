package com.blaie.blaie_be.configuration.security;

import com.blaie.blaie_be.auth.infrastructure.security.AuthRequestFilter;
import com.blaie.blaie_be.auth.infrastructure.security.EmailVerificationRequiredFilter;
import com.blaie.blaie_be.core.ratelimit.filter.RateLimitFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityFilterRegistrationTest {
    private final ApplicationSecurityConfiguration securityConfig =
            new ApplicationSecurityConfiguration();

    @Test
    void customSecurityFiltersAreNotAutoRegisteredOutsideSecurityFilterChain() {
        AuthRequestFilter authFilter = mock(AuthRequestFilter.class);
        RateLimitFilter rateLimitFilter = mock(RateLimitFilter.class);
        EmailVerificationRequiredFilter verificationFilter = mock(EmailVerificationRequiredFilter.class);

        assertThat(securityConfig.authRequestFilterRegistration(authFilter).isEnabled()).isFalse();
        assertThat(securityConfig.rateLimitFilterRegistration(rateLimitFilter).isEnabled()).isFalse();
        assertThat(securityConfig.emailVerificationFilterRegistration(verificationFilter).isEnabled()).isFalse();
    }
}

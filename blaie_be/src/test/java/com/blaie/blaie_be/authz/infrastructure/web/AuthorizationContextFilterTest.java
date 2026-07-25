package com.blaie.blaie_be.authz.infrastructure.web;

import com.blaie.blaie_be.authz.application.UserPermissionResolver;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationContextFilterTest {
    @AfterEach
    void clearContext() {
        CurrentUserHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void enrichesCurrentUserAndSecurityAuthoritiesAfterAuthentication() throws Exception {
        CurrentUser identity = new CurrentUser("user-1", false, Set.of());
        CurrentUserHolder.set(identity);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        identity,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        UserPermissionResolver resolver = mock(UserPermissionResolver.class);
        when(resolver.resolve(identity)).thenReturn(Set.of(PermissionAction.CAPTURE_READ.key()));
        AuthorizationContextFilter filter = new AuthorizationContextFilter(resolver);
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/captures"),
                new MockHttpServletResponse(),
                chain
        );

        verify(chain).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(CurrentUserHolder.requireCurrentUser().permissions())
                .containsExactly(PermissionAction.CAPTURE_READ.key());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_USER", PermissionAction.CAPTURE_READ.key());
    }
}

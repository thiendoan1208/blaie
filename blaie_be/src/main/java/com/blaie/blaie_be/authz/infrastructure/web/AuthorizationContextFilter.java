package com.blaie.blaie_be.authz.infrastructure.web;

import com.blaie.blaie_be.authz.application.UserPermissionResolver;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthorizationContextFilter extends OncePerRequestFilter {
    private final UserPermissionResolver permissionResolver;

    public AuthorizationContextFilter(UserPermissionResolver permissionResolver) {
        this.permissionResolver = permissionResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        CurrentUserHolder.current().ifPresent(this::enrichAuthorizationContext);
        filterChain.doFilter(request, response);
    }

    private void enrichAuthorizationContext(CurrentUser identity) {
        Set<String> permissions = permissionResolver.resolve(identity);
        CurrentUser authorizedUser = new CurrentUser(
                identity.userId(),
                identity.admin(),
                permissions
        );
        CurrentUserHolder.set(authorizedUser);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return;
        }
        LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>(
                authentication.getAuthorities()
        );
        permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authorizedUser,
                        authentication.getCredentials(),
                        authorities
                )
        );
    }
}

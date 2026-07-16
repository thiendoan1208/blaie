package com.blaie.blaie_be.auth.infrastructure.security;

import com.blaie.blaie_be.auth.domain.AuthConstants;
import com.blaie.blaie_be.auth.infrastructure.persistence.UserEntity;
import com.blaie.blaie_be.auth.infrastructure.persistence.UserRepository;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRequestFilter extends OncePerRequestFilter {

    private final AuthTokenService authTokenService;
    private final UserRepository userRepository;

    public AuthRequestFilter(AuthTokenService authTokenService, UserRepository userRepository) {
        this.authTokenService = authTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            resolveAccessToken(request)
                    .flatMap(authTokenService::parseAccessToken)
                    .flatMap(claims -> userRepository.findByIdAndStatus(claims.userId(), AuthConstants.USER_STATUS_ACTIVE))
                    .ifPresent(this::setCurrentUser);
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void setCurrentUser(UserEntity user) {
        CurrentUser currentUser = new CurrentUser(user.id().toString(), user.admin(), Set.of());
        CurrentUserHolder.set(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, List.of())
        );
    }

    private Optional<String> resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        Optional<String> bearerToken = BearerTokenResolver.resolve(authorization);
        if (bearerToken.isPresent()) {
            return bearerToken;
        }
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (AuthCookieService.ACCESS_COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}

package com.blaie.blaie_be.auth.infrastructure.security;

import com.blaie.blaie_be.auth.infrastructure.persistence.AuthIdentityRepository;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import com.blaie.blaie_be.core.security.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class EmailVerificationRequiredFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH_ALLOWLIST = Set.of(
            "/api/v1/auth/me",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf",
            "/api/v1/auth/email/verification",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/google/start",
            "/api/v1/auth/google/callback"
    );

    private final AuthIdentityRepository authIdentityRepository;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public EmailVerificationRequiredFilter(
            AuthIdentityRepository authIdentityRepository,
            SecurityErrorResponseWriter errorResponseWriter
    ) {
        this.authIdentityRepository = authIdentityRepository;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!requiresEmailVerification(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        UUID userId = CurrentUserHolder.current()
                .map(CurrentUser::userId)
                .map(this::parseUserId)
                .orElse(null);
        if (userId == null || authIdentityRepository.existsByUser_IdAndEmailVerifiedTrue(userId)) {
            filterChain.doFilter(request, response);
            return;
        }
        errorResponseWriter.write(response, ErrorCode.EMAIL_NOT_VERIFIED);
    }

    private boolean requiresEmailVerification(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/")
                && !AUTH_ALLOWLIST.contains(path)
                && !path.startsWith("/api/v1/auth/register")
                && !path.startsWith("/api/v1/auth/login");
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}

package com.blaie.blaie_be.core.ratelimit.policy;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitPolicy;
import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import com.blaie.blaie_be.core.ratelimit.filter.CachedBodyHttpServletRequest;
import com.blaie.blaie_be.core.ratelimit.support.ClientIpResolver;
import com.blaie.blaie_be.core.ratelimit.support.SubjectHasher;
import com.blaie.blaie_be.core.security.AuthCookieNames;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RateLimitPolicyResolver {
    private final RateLimitProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final SubjectHasher subjectHasher;
    private final ObjectMapper objectMapper;

    public RateLimitPolicyResolver(
            RateLimitProperties properties,
            ClientIpResolver clientIpResolver,
            SubjectHasher subjectHasher,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
        this.subjectHasher = subjectHasher;
        this.objectMapper = objectMapper;
    }

    public boolean needsCachedBody(HttpServletRequest request) {
        if (!isJsonWrite(request)) {
            return false;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();
        return HttpMethod.POST.matches(method)
                && ("/api/v1/auth/login".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/password-reset/request".equals(path)
                || "/api/v1/auth/password-reset/confirm".equals(path));
    }

    public Optional<RateLimitRequest> resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String ipSubject = hashedPart("ip", clientIpResolver.resolve(request));

        if (HttpMethod.POST.matches(method) && "/api/v1/auth/login".equals(path)) {
            return request("auth-login", properties.authLogin(), ipSubject, fieldPart(request, "identifier"));
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/auth/register".equals(path)) {
            return request("auth-register", properties.authRegister(), ipSubject, fieldPart(request, "email"));
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/auth/password-reset/request".equals(path)) {
            return request("password-reset-request", properties.passwordResetRequest(), ipSubject, fieldPart(request, "email"));
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/auth/password-reset/confirm".equals(path)) {
            return request("password-reset-confirm", properties.passwordResetConfirm(), ipSubject, fieldPart(request, "email"));
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/auth/email/verification".equals(path)) {
            return request("email-verification", properties.emailVerification(), userOrIpSubject(ipSubject));
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/auth/refresh".equals(path)) {
            return request("auth-refresh", properties.authRefresh(), ipSubject, cookiePart(request, AuthCookieNames.REFRESH_COOKIE_NAME));
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/auth/logout".equals(path)) {
            return request("auth-logout", properties.authLogout(), userOrIpSubject(ipSubject), cookiePart(request, AuthCookieNames.REFRESH_COOKIE_NAME));
        }
        if (HttpMethod.GET.matches(method) && "/api/v1/auth/csrf".equals(path)) {
            return request("csrf", properties.csrf(), ipSubject);
        }
        if (HttpMethod.GET.matches(method) && "/api/v1/auth/google/start".equals(path)) {
            return request("google-start", properties.googleStart(), ipSubject);
        }
        if (HttpMethod.GET.matches(method) && "/api/v1/auth/google/callback".equals(path)) {
            return request("google-callback", properties.googleCallback(), ipSubject);
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/captures/text".equals(path)) {
            return request("capture-text", properties.captureText(), userSubject(), ipSubject);
        }
        if (HttpMethod.POST.matches(method) && isCaptureRetryPath(path)) {
            return request("capture-retry", properties.captureRetry(), userSubject(), ipSubject);
        }
        return Optional.empty();
    }

    private Optional<RateLimitRequest> request(String policyName, RateLimitPolicy policy, String... subjectParts) {
        if (policy == null || !policy.enabled()) {
            return Optional.empty();
        }
        boolean failOpen = policy.failOpen() == null ? properties.failOpen() : policy.failOpen();
        return Optional.of(new RateLimitRequest(
                policyName,
                String.join(":", subjectParts),
                policy.windows(),
                failOpen
        ));
    }

    private String userOrIpSubject(String ipSubject) {
        return CurrentUserHolder.current()
                .map(user -> hashedPart("user", user.userId()))
                .orElse(ipSubject);
    }

    private String userSubject() {
        return CurrentUserHolder.current()
                .map(user -> hashedPart("user", user.userId()))
                .orElseGet(() -> hashedPart("user", "anonymous"));
    }

    private boolean isCaptureRetryPath(String path) {
        return path.matches("/api/v1/captures/[0-9a-fA-F-]{36}/retry");
    }

    private String fieldPart(HttpServletRequest request, String fieldName) {
        return hashedPart(fieldName, bodyField(request, fieldName).orElse("missing"));
    }

    private Optional<String> bodyField(HttpServletRequest request, String fieldName) {
        if (!(request instanceof CachedBodyHttpServletRequest cachedRequest) || cachedRequest.cachedBody().length == 0) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(cachedRequest.cachedBody());
            JsonNode field = root.path(fieldName);
            return field.isString() ? Optional.of(field.asString()) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String cookiePart(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return hashedPart(cookieName, "missing");
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return hashedPart(cookieName, cookie.getValue());
            }
        }
        return hashedPart(cookieName, "missing");
    }

    private String hashedPart(String purpose, String value) {
        return purpose + "." + subjectHasher.hash(purpose, value);
    }

    private boolean isJsonWrite(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("application/json");
    }
}

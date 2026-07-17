package com.blaie.blaie_be.core.ratelimit.filter;

import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import com.blaie.blaie_be.core.ratelimit.limiter.RateLimitDecision;
import com.blaie.blaie_be.core.ratelimit.limiter.RateLimitBackendUnavailableException;
import com.blaie.blaie_be.core.ratelimit.limiter.RateLimiter;
import com.blaie.blaie_be.core.ratelimit.policy.RateLimitPolicyResolver;
import com.blaie.blaie_be.core.ratelimit.policy.RateLimitRequest;
import com.blaie.blaie_be.core.security.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final long MAX_CACHED_BODY_BYTES = 8192;

    private final RateLimitProperties properties;
    private final RateLimitPolicyResolver policyResolver;
    private final RateLimiter rateLimiter;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public RateLimitFilter(
            RateLimitProperties properties,
            RateLimitPolicyResolver policyResolver,
            RateLimiter rateLimiter,
            SecurityErrorResponseWriter errorResponseWriter
    ) {
        this.properties = properties;
        this.policyResolver = policyResolver;
        this.rateLimiter = rateLimiter;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled() || HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest effectiveRequest = maybeCacheBody(request);
        RateLimitRequest rateLimitRequest = policyResolver.resolve(effectiveRequest).orElse(null);
        if (rateLimitRequest == null) {
            filterChain.doFilter(effectiveRequest, response);
            return;
        }

        RateLimitDecision decision;
        try {
            decision = rateLimiter.check(rateLimitRequest);
        } catch (RateLimitBackendUnavailableException exception) {
            errorResponseWriter.write(
                    response,
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Capture submission is temporarily unavailable",
                    Map.of("Retry-After", "30")
            );
            return;
        }
        if (decision.allowed()) {
            filterChain.doFilter(effectiveRequest, response);
            return;
        }

        long retryAfterSeconds = retryAfterSeconds(decision.retryAfter());
        errorResponseWriter.write(
                response,
                ErrorCode.RATE_LIMITED,
                ErrorCode.RATE_LIMITED.defaultMessage(),
                Map.of("Retry-After", String.valueOf(retryAfterSeconds))
        );
    }

    private HttpServletRequest maybeCacheBody(HttpServletRequest request) throws IOException {
        if (!policyResolver.needsCachedBody(request)) {
            return request;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0 || contentLength > MAX_CACHED_BODY_BYTES) {
            return request;
        }
        return new CachedBodyHttpServletRequest(request);
    }

    private long retryAfterSeconds(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            return 1L;
        }
        return Math.max(1L, (long) Math.ceil(retryAfter.toMillis() / 1000.0d));
    }
}

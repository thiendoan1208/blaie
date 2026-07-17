package com.blaie.blaie_be.core.ratelimit.filter;

import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import com.blaie.blaie_be.core.ratelimit.config.RateLimitWindow;
import com.blaie.blaie_be.core.ratelimit.limiter.RateLimitBackendUnavailableException;
import com.blaie.blaie_be.core.ratelimit.limiter.RateLimitDecision;
import com.blaie.blaie_be.core.ratelimit.limiter.RateLimiter;
import com.blaie.blaie_be.core.ratelimit.policy.RateLimitPolicyResolver;
import com.blaie.blaie_be.core.ratelimit.policy.RateLimitRequest;
import com.blaie.blaie_be.core.security.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {
    @Test
    void allowedDecisionContinuesTheFilterChain() throws Exception {
        Fixture fixture = fixture();
        when(fixture.rateLimiter.check(fixture.request))
                .thenReturn(RateLimitDecision.allowed("capture-text"));

        fixture.filter.doFilterInternal(fixture.httpRequest, fixture.response, fixture.chain);

        verify(fixture.chain).doFilter(fixture.httpRequest, fixture.response);
        verifyNoInteractions(fixture.errorWriter);
    }

    @Test
    void deniedDecisionReturns429WithRoundedUpRetryAfterWithoutCallingApplication() throws Exception {
        Fixture fixture = fixture();
        when(fixture.rateLimiter.check(fixture.request))
                .thenReturn(RateLimitDecision.denied("capture-text", Duration.ofMillis(1_001)));

        fixture.filter.doFilterInternal(fixture.httpRequest, fixture.response, fixture.chain);

        ArgumentCaptor<Map<String, String>> headers = headersCaptor();
        verify(fixture.errorWriter).write(
                eq(fixture.response),
                eq(ErrorCode.RATE_LIMITED),
                eq(ErrorCode.RATE_LIMITED.defaultMessage()),
                headers.capture()
        );
        assertThat(headers.getValue()).containsEntry("Retry-After", "2");
        verifyNoInteractions(fixture.chain);
    }

    @Test
    void failClosedBackendFailureReturns503BeforeCallingApplication() throws Exception {
        Fixture fixture = fixture();
        when(fixture.rateLimiter.check(fixture.request))
                .thenThrow(new RateLimitBackendUnavailableException(new RuntimeException("Redis down")));

        fixture.filter.doFilterInternal(fixture.httpRequest, fixture.response, fixture.chain);

        ArgumentCaptor<Map<String, String>> headers = headersCaptor();
        verify(fixture.errorWriter).write(
                eq(fixture.response),
                eq(ErrorCode.SERVICE_UNAVAILABLE),
                eq("Capture submission is temporarily unavailable"),
                headers.capture()
        );
        assertThat(headers.getValue()).containsEntry("Retry-After", "30");
        verifyNoInteractions(fixture.chain);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Map<String, String>> headersCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }

    private Fixture fixture() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitPolicyResolver resolver = mock(RateLimitPolicyResolver.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        SecurityErrorResponseWriter errorWriter = mock(SecurityErrorResponseWriter.class);
        RateLimitFilter filter = new RateLimitFilter(properties, resolver, rateLimiter, errorWriter);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/api/v1/captures/text");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        RateLimitRequest request = new RateLimitRequest(
                "capture-text",
                "user.hash:ip.hash",
                List.of(new RateLimitWindow(10, Duration.ofMinutes(1))),
                false
        );
        when(resolver.resolve(any())).thenReturn(Optional.of(request));
        return new Fixture(filter, rateLimiter, errorWriter, httpRequest, response, chain, request);
    }

    private record Fixture(
            RateLimitFilter filter,
            RateLimiter rateLimiter,
            SecurityErrorResponseWriter errorWriter,
            MockHttpServletRequest httpRequest,
            MockHttpServletResponse response,
            FilterChain chain,
            RateLimitRequest request
    ) {
    }
}

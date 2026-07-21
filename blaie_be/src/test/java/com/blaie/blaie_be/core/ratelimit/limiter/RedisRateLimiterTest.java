package com.blaie.blaie_be.core.ratelimit.limiter;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitProperties;
import com.blaie.blaie_be.core.ratelimit.config.RateLimitWindow;
import com.blaie.blaie_be.core.ratelimit.policy.RateLimitRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRateLimiterTest {
    @Test
    void deniesWhenAnyWindowIsExceededAndUsesLongestRetryAfter() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        stub(redisTemplate, List.of(11L, 12_500L), List.of(61L, 305_000L));
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, properties());
        RateLimitRequest request = new RateLimitRequest(
                "capture-text",
                "user.hash:ip.hash",
                List.of(
                        new RateLimitWindow(10, Duration.ofMinutes(1)),
                        new RateLimitWindow(60, Duration.ofMinutes(10))
                ),
                false
        );

        RateLimitDecision decision = limiter.check(request);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofMillis(305_000));
    }

    @Test
    void failClosedPolicyRejectsNullOrMalformedRedisScriptResults() {
        StringRedisTemplate nullTemplate = mock(StringRedisTemplate.class);
        RedisRateLimiter nullLimiter = new RedisRateLimiter(nullTemplate, properties());
        RateLimitRequest request = request(false);

        assertThatThrownBy(() -> nullLimiter.check(request))
                .isInstanceOf(RateLimitBackendUnavailableException.class);

        StringRedisTemplate malformedTemplate = mock(StringRedisTemplate.class);
        stub(malformedTemplate, List.of("not-a-counter", 1_000L));
        RedisRateLimiter malformedLimiter = new RedisRateLimiter(malformedTemplate, properties());
        assertThatThrownBy(() -> malformedLimiter.check(request))
                .isInstanceOf(RateLimitBackendUnavailableException.class);
    }

    @Test
    void failOpenPolicyAllowsRedisConnectionFailure() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(),
                anyList(),
                any()
        )).thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, properties());

        assertThat(limiter.check(request(true)).allowed()).isTrue();
    }

    private RateLimitRequest request(boolean failOpen) {
        return new RateLimitRequest(
                "capture-text",
                "user.hash:ip.hash",
                List.of(new RateLimitWindow(10, Duration.ofMinutes(1))),
                failOpen
        );
    }

    private RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setSubjectHmacSecret("rate-limit-test-secret-at-least-32-bytes");
        return properties;
    }

    private void stub(StringRedisTemplate redisTemplate, List<?>... results) {
        AtomicInteger invocation = new AtomicInteger();
        doAnswer(ignored -> results[Math.min(invocation.getAndIncrement(), results.length - 1)])
                .when(redisTemplate).execute(
                any(),
                anyList(),
                any()
        );
    }
}

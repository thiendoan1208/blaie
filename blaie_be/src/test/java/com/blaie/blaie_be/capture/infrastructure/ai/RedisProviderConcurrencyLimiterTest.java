package com.blaie.blaie_be.capture.infrastructure.ai;

import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisProviderConcurrencyLimiterTest {

    @Test
    void waitsUntilRedisAtomicallyGrantsTheProviderPermit() {
        AiProviderConcurrencyProperties properties = properties();
        properties.setPollInterval(Duration.ofMillis(1));
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        String key = properties.keyFor("deepseek");
        doReturn(List.of(0L, 1L), List.of(1L, 1L)).when(redisTemplate).execute(
                eq(RedisProviderConcurrencyLimiter.ACQUIRE),
                eq(List.of(key)),
                any(String.class),
                eq("1"),
                eq("30000")
        );
        RedisProviderConcurrencyLimiter limiter = limiter(redisTemplate, properties);

        ProviderConcurrencyLimiter.Permit permit = limiter.acquire("deepseek");

        verify(redisTemplate, times(2)).execute(
                eq(RedisProviderConcurrencyLimiter.ACQUIRE),
                eq(List.of(key)),
                any(String.class),
                eq("1"),
                eq("30000")
        );
        permit.close();
    }

    @Test
    void releaseIsOwnerTokenFencedAndIdempotent() {
        AiProviderConcurrencyProperties properties = properties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        String key = properties.keyFor("deepseek");
        doReturn(List.of(1L, 1L)).when(redisTemplate).execute(
                eq(RedisProviderConcurrencyLimiter.ACQUIRE),
                eq(List.of(key)),
                any(String.class),
                eq("1"),
                eq("30000")
        );
        RedisProviderConcurrencyLimiter limiter = limiter(redisTemplate, properties);

        ProviderConcurrencyLimiter.Permit permit = limiter.acquire("deepseek");
        permit.close();
        permit.close();

        verify(redisTemplate, times(1)).execute(
                eq(RedisProviderConcurrencyLimiter.RELEASE),
                eq(List.of(key)),
                any(String.class)
        );
        assertThat(RedisProviderConcurrencyLimiter.RELEASE.getScriptAsString())
                .contains("ZREM", "ARGV[1]");
    }

    @Test
    void acquireFailureFailsClosedAsASystemRetryableClassificationError() {
        AiProviderConcurrencyProperties properties = properties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFailureException redisFailure =
                new RedisConnectionFailureException("simulated Redis outage");
        when(redisTemplate.execute(
                eq(RedisProviderConcurrencyLimiter.ACQUIRE),
                eq(List.of(properties.keyFor("deepseek"))),
                any(String.class),
                eq("1"),
                eq("30000")
        )).thenThrow(redisFailure);
        RedisProviderConcurrencyLimiter limiter = limiter(redisTemplate, properties);

        assertThatThrownBy(() -> limiter.acquire("deepseek"))
                .isInstanceOf(TextClassificationException.class)
                .satisfies(exception -> {
                    TextClassificationException classificationException =
                            (TextClassificationException) exception;
                    assertThat(classificationException.failureCode())
                            .isEqualTo("ai_concurrency_backend_unavailable");
                    assertThat(classificationException.failureClass())
                            .isEqualTo(TextClassificationFailureClass.SYSTEM_RETRYABLE);
                    assertThat(classificationException.getCause()).isSameAs(redisFailure);
                });
    }

    @Test
    void interruptedWaitFailsClosedWithoutCallingRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisProviderConcurrencyLimiter limiter = limiter(redisTemplate, properties());

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> limiter.acquire("deepseek"))
                    .isInstanceOf(TextClassificationException.class)
                    .satisfies(exception -> {
                        TextClassificationException classificationException =
                                (TextClassificationException) exception;
                        assertThat(classificationException.failureCode())
                                .isEqualTo("ai_concurrency_wait_interrupted");
                        assertThat(classificationException.failureClass())
                                .isEqualTo(TextClassificationFailureClass.SYSTEM_RETRYABLE);
                    });
            verifyNoInteractions(redisTemplate);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void releaseFailureDoesNotReplaceASuccessfulProviderResult() {
        AiProviderConcurrencyProperties properties = properties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        String key = properties.keyFor("deepseek");
        doReturn(List.of(1L, 1L)).when(redisTemplate).execute(
                eq(RedisProviderConcurrencyLimiter.ACQUIRE),
                eq(List.of(key)),
                any(String.class),
                eq("1"),
                eq("30000")
        );
        when(redisTemplate.execute(
                eq(RedisProviderConcurrencyLimiter.RELEASE),
                eq(List.of(key)),
                any(String.class)
        )).thenThrow(new RedisConnectionFailureException("simulated release outage"));
        RedisProviderConcurrencyLimiter limiter = limiter(redisTemplate, properties);
        ProviderConcurrencyLimiter.Permit permit = limiter.acquire("deepseek");

        assertThatCode(permit::close).doesNotThrowAnyException();
    }

    @Test
    void disabledLimiterDoesNotTouchRedis() {
        AiProviderConcurrencyProperties properties = properties();
        properties.setEnabled(false);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisProviderConcurrencyLimiter limiter = limiter(redisTemplate, properties);

        ProviderConcurrencyLimiter.Permit permit = limiter.acquire("deepseek");
        permit.close();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void acquireScriptUsesRedisTimeAndLeaseBasedCleanup() {
        assertThat(RedisProviderConcurrencyLimiter.ACQUIRE.getScriptAsString())
                .contains("redis.call('TIME')")
                .contains("ZREMRANGEBYSCORE")
                .contains("ZADD", "'NX'")
                .contains("PEXPIRE");
        assertThat(RedisProviderConcurrencyLimiter.RENEW.getScriptAsString())
                .contains("redis.call('TIME')")
                .contains("ZSCORE")
                .contains("ZADD", "'XX'")
                .contains("PEXPIRE");
    }

    @Test
    void renewalFailureIsLoggedAndDoesNotEscapeTheSchedulerTask() {
        AiProviderConcurrencyProperties properties = properties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        org.mockito.ArgumentCaptor<Runnable> renewal = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(
                renewal.capture(),
                any(Instant.class),
                eq(properties.renewalInterval())
        );
        doReturn(List.of(1L, 1L)).when(redisTemplate).execute(
                eq(RedisProviderConcurrencyLimiter.ACQUIRE),
                eq(List.of(properties.keyFor("deepseek"))),
                any(String.class),
                eq("1"),
                eq("30000")
        );
        doThrow(new RedisConnectionFailureException("simulated renewal outage"))
                .when(redisTemplate).execute(
                        eq(RedisProviderConcurrencyLimiter.RENEW),
                        eq(List.of(properties.keyFor("deepseek"))),
                        any(String.class),
                        eq("30000")
                );
        RedisProviderConcurrencyLimiter limiter =
                new RedisProviderConcurrencyLimiter(redisTemplate, properties, scheduler);
        ProviderConcurrencyLimiter.Permit permit = limiter.acquire("deepseek");

        assertThatCode(() -> renewal.getValue().run()).doesNotThrowAnyException();
        permit.close();
        verify(future).cancel(false);
    }

    private AiProviderConcurrencyProperties properties() {
        return new AiProviderConcurrencyProperties();
    }

    private RedisProviderConcurrencyLimiter limiter(
            StringRedisTemplate redisTemplate,
            AiProviderConcurrencyProperties properties
    ) {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                eq(properties.renewalInterval())
        );
        return new RedisProviderConcurrencyLimiter(redisTemplate, properties, scheduler);
    }
}

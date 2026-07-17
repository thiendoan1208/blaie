package com.blaie.blaie_be.capture.infrastructure.async;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStreamCommands.MinIdTrimStrategy;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOperator;
import org.springframework.data.redis.connection.RedisStreamCommands.XTrimOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCaptureStreamCleanerTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void trimsByAgeWithApproximateMinIdInsteadOfMaxLength() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setStreamRetention(Duration.ofHours(6));
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.trim(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(XTrimOptions.class)))
                .thenReturn(4L);
        RedisCaptureStreamCleaner cleaner = new RedisCaptureStreamCleaner(
                redisTemplate,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        cleaner.cleanup();

        ArgumentCaptor<XTrimOptions> options = ArgumentCaptor.forClass(XTrimOptions.class);
        verify(streams).trim(org.mockito.ArgumentMatchers.eq(properties.streamKey()), options.capture());
        assertThat(options.getValue().getTrimOptions().getTrimOperator()).isEqualTo(TrimOperator.APPROXIMATE);
        assertThat(options.getValue().getTrimOptions().getTrimStrategy())
                .isInstanceOfSatisfying(MinIdTrimStrategy.class, strategy ->
                        assertThat(strategy.threshold().getValue())
                                .isEqualTo(NOW.minus(Duration.ofHours(6)).toEpochMilli() + "-0")
                );
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureDoesNotBreakFutureScheduledCleanupCycles() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.trim(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(XTrimOptions.class)))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        RedisCaptureStreamCleaner cleaner = new RedisCaptureStreamCleaner(
                redisTemplate,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatCode(cleaner::cleanup).doesNotThrowAnyException();
    }
}

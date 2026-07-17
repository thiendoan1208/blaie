package com.blaie.blaie_be.capture.infrastructure.async;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XTrimOptions;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "blaie.capture.processing",
        name = "recovery-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisCaptureStreamCleaner {
    private static final Logger log = LoggerFactory.getLogger(RedisCaptureStreamCleaner.class);

    private final StringRedisTemplate redisTemplate;
    private final CaptureProcessingProperties properties;
    private final Clock clock;

    public RedisCaptureStreamCleaner(
            StringRedisTemplate redisTemplate,
            CaptureProcessingProperties properties,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${blaie.capture.processing.stream-cleanup-interval:1h}")
    public void cleanup() {
        Instant cutoff = clock.instant().minus(properties.streamRetention());
        RecordId minimumRetainedId = RecordId.of(Math.max(0, cutoff.toEpochMilli()), 0);
        try {
            Long trimmed = redisTemplate.opsForStream().trim(
                    properties.streamKey(),
                    XTrimOptions.trim(TrimOptions.minId(minimumRetainedId).approximate())
            );
            if (trimmed != null && trimmed > 0) {
                log.info("Trimmed {} expired capture Redis Stream records", trimmed);
            }
        } catch (DataAccessException exception) {
            log.warn("Capture Redis Stream cleanup is temporarily unavailable: {}", exception.getMessage());
        }
    }
}

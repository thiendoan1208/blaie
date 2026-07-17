package com.blaie.blaie_be.capture.infrastructure.async;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "blaie.capture.processing",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisCaptureMessageFinalizer {
    static final DefaultRedisScript<Long> ACKNOWLEDGE_AND_DELETE = new DefaultRedisScript<>(
            """
            local acknowledged = redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            redis.call('XDEL', KEYS[1], ARGV[2])
            return acknowledged
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final CaptureProcessingProperties properties;

    public RedisCaptureMessageFinalizer(
            StringRedisTemplate redisTemplate,
            CaptureProcessingProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void acknowledgeAndDelete(RecordId recordId) {
        redisTemplate.execute(
                ACKNOWLEDGE_AND_DELETE,
                List.of(properties.streamKey()),
                properties.consumerGroup(),
                recordId.getValue()
        );
    }
}

package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "blaie.capture.processing",
        name = "publisher-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisCaptureJobPublisher {
    private final StringRedisTemplate redisTemplate;
    private final CaptureProcessingProperties properties;

    public RedisCaptureJobPublisher(
            StringRedisTemplate redisTemplate,
            CaptureProcessingProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @ApplicationModuleListener(id = "capture-text-job-redis-publisher")
    public void publish(TextCaptureQueuedEvent event) {
        redisTemplate.opsForStream().add(
                StreamRecords.<String, String, String>mapBacked(Map.of(
                        "eventId", event.eventId().toString(),
                        "jobId", event.jobId().toString(),
                        "captureId", event.captureId().toString(),
                        "dispatchGeneration", Integer.toString(event.dispatchGeneration())
                )).withStreamKey(properties.streamKey())
        );
    }
}

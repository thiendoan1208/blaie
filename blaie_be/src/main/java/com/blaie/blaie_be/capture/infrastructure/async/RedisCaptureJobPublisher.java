package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.core.request.MdcContextScope;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(RedisCaptureJobPublisher.class);
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
        try (MdcContextScope _ = MdcContextScope.replace(Map.of(
                "requestId", event.originRequestId(),
                "eventId", event.eventId().toString(),
                "jobId", event.jobId().toString(),
                "captureId", event.captureId().toString(),
                "dispatchGeneration", Integer.toString(event.dispatchGeneration())
        ))) {
            try {
                redisTemplate.opsForStream().add(
                        StreamRecords.<String, String, String>mapBacked(Map.of(
                                "eventId", event.eventId().toString(),
                                "jobId", event.jobId().toString(),
                                "captureId", event.captureId().toString(),
                                "dispatchGeneration", Integer.toString(event.dispatchGeneration()),
                                "originRequestId", event.originRequestId()
                        )).withStreamKey(properties.streamKey())
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Capture job dispatch publication failed: eventId={}, jobId={}, captureId={}",
                        event.eventId(),
                        event.jobId(),
                        event.captureId()
                );
                throw exception;
            }
        }
    }
}

package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.CaptureJobProcessor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "blaie.capture.processing",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisCaptureJobWorker {
    private static final Logger log = LoggerFactory.getLogger(RedisCaptureJobWorker.class);
    private static final Map<String, String> BOOTSTRAP_RECORD = Map.of("bootstrap", "true");

    private final StringRedisTemplate redisTemplate;
    private final CaptureProcessingProperties properties;
    private final CaptureJobProcessor processor;
    private volatile boolean consumerGroupReady;

    public RedisCaptureJobWorker(
            StringRedisTemplate redisTemplate,
            CaptureProcessingProperties properties,
            CaptureJobProcessor processor
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${blaie.capture.processing.poll-interval:1s}")
    public void poll() {
        try {
            if (!ensureConsumerGroup()) {
                return;
            }
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            processRecords(reclaimStaleRecords(streams), streams);
            List<MapRecord<String, String, String>> records = readNewRecords(streams);
            processRecords(records, streams);
        } catch (DataAccessException exception) {
            consumerGroupReady = false;
            log.warn("Capture Redis worker is temporarily unavailable: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Capture Redis worker poll failed", exception);
        }
    }

    @SuppressWarnings("unchecked") // Spring's read API requires a generic varargs StreamOffset array.
    private List<MapRecord<String, String, String>> readNewRecords(
            StreamOperations<String, String, String> streams
    ) {
        return streams.read(
                Consumer.from(properties.consumerGroup(), properties.consumerName()),
                StreamReadOptions.empty()
                        .count(properties.batchSize())
                        .block(properties.readBlock()),
                StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed())
        );
    }

    private List<MapRecord<String, String, String>> reclaimStaleRecords(
            StreamOperations<String, String, String> streams
    ) {
        PendingMessages pending = streams.pending(
                properties.streamKey(),
                properties.consumerGroup(),
                Range.unbounded(),
                properties.batchSize(),
                properties.leaseDuration()
        );
        if (pending.isEmpty()) {
            return List.of();
        }
        RecordId[] recordIds = pending.stream()
                .map(message -> message.getId())
                .toArray(RecordId[]::new);
        return streams.claim(
                properties.streamKey(),
                properties.consumerGroup(),
                properties.consumerName(),
                properties.leaseDuration(),
                recordIds
        );
    }

    private void processRecords(
            List<MapRecord<String, String, String>> records,
            StreamOperations<String, String, String> streams
    ) {
        if (records == null) {
            return;
        }
        for (MapRecord<String, String, String> record : records) {
            String jobId = record.getValue().get("jobId");
            if (jobId == null) {
                streams.acknowledge(properties.streamKey(), properties.consumerGroup(), record.getId());
                continue;
            }
            try {
                if (processor.process(UUID.fromString(jobId), properties.consumerName())) {
                    streams.acknowledge(properties.streamKey(), properties.consumerGroup(), record.getId());
                }
            } catch (IllegalArgumentException exception) {
                log.warn("Discarding malformed capture job message {}", record.getId());
                streams.acknowledge(properties.streamKey(), properties.consumerGroup(), record.getId());
            } catch (RuntimeException exception) {
                log.error("Capture job message {} could not be processed", record.getId(), exception);
            }
        }
    }

    private boolean ensureConsumerGroup() {
        if (consumerGroupReady) {
            return true;
        }
        StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
        RecordId bootstrap = streams.add(properties.streamKey(), BOOTSTRAP_RECORD);
        try {
            streams.createGroup(
                    properties.streamKey(),
                    ReadOffset.from("0-0"),
                    properties.consumerGroup()
            );
        } catch (DataAccessException exception) {
            if (!containsBusyGroup(exception)) {
                throw exception;
            }
        } finally {
            if (bootstrap != null) {
                streams.delete(properties.streamKey(), bootstrap);
            }
        }
        consumerGroupReady = true;
        return true;
    }

    private boolean containsBusyGroup(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

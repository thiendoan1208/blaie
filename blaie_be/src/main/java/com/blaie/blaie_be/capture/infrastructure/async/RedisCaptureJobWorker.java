package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.CaptureJobProcessor;
import com.blaie.blaie_be.core.request.MdcContextScope;
import com.blaie.blaie_be.core.request.RequestIdPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "blaie.capture.processing",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisCaptureJobWorker {
    private static final Logger log = LoggerFactory.getLogger(RedisCaptureJobWorker.class);
    private static final Map<String, String> BOOTSTRAP_RECORD = Map.of("bootstrap", "true");

    private final StringRedisTemplate redisTemplate;
    private final CaptureProcessingProperties properties;
    private final CaptureJobProcessor processor;
    private final RedisCaptureMessageFinalizer messageFinalizer;
    private final ThreadPoolTaskExecutor jobExecutor;
    private final AtomicBoolean acceptingWork = new AtomicBoolean(true);
    private volatile boolean consumerGroupReady;

    public RedisCaptureJobWorker(
            StringRedisTemplate redisTemplate,
            CaptureProcessingProperties properties,
            CaptureJobProcessor processor,
            RedisCaptureMessageFinalizer messageFinalizer,
            @Qualifier(CaptureAsyncConfiguration.JOB_EXECUTOR) ThreadPoolTaskExecutor jobExecutor
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.processor = processor;
        this.messageFinalizer = messageFinalizer;
        this.jobExecutor = jobExecutor;
    }

    @Scheduled(fixedDelayString = "${blaie.capture.processing.poll-interval:1s}")
    public void poll() {
        if (!acceptingWork.get()) {
            return;
        }
        try {
            if (!ensureConsumerGroup()) {
                return;
            }
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            int availableCapacity = availableCapacity();
            if (availableCapacity == 0) {
                return;
            }
            int reclaimed = processRecords(
                    reclaimStaleRecords(streams, availableCapacity)
            );
            if (reclaimed > 0) {
                return;
            }
            if (!acceptingWork.get()) {
                return;
            }
            availableCapacity = availableCapacity();
            if (availableCapacity == 0) {
                return;
            }
            List<MapRecord<String, String, String>> records = readNewRecords(streams, availableCapacity);
            processRecords(records);
        } catch (DataAccessException exception) {
            consumerGroupReady = false;
            log.warn("Capture Redis worker is temporarily unavailable: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Capture Redis worker poll failed", exception);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stopAcceptingWork() {
        acceptingWork.set(false);
    }

    @SuppressWarnings("unchecked") // Spring's read API requires a generic varargs StreamOffset array.
    private List<MapRecord<String, String, String>> readNewRecords(
            StreamOperations<String, String, String> streams,
            int limit
    ) {
        return streams.read(
                Consumer.from(properties.consumerGroup(), properties.consumerName()),
                StreamReadOptions.empty()
                        .count(limit)
                        .block(properties.readBlock()),
                StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed())
        );
    }

    private List<MapRecord<String, String, String>> reclaimStaleRecords(
            StreamOperations<String, String, String> streams,
            int limit
    ) {
        PendingMessages pending = streams.pending(
                properties.streamKey(),
                properties.consumerGroup(),
                Range.unbounded(),
                limit,
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

    private int processRecords(List<MapRecord<String, String, String>> records) {
        if (records == null) {
            return 0;
        }
        int submitted = 0;
        for (MapRecord<String, String, String> record : records) {
            String jobId = record.getValue().get("jobId");
            String dispatchGeneration = record.getValue().get("dispatchGeneration");
            if (jobId == null || dispatchGeneration == null) {
                messageFinalizer.acknowledgeAndDelete(record.getId());
                continue;
            }
            if (!acceptingWork.get()) {
                return submitted;
            }
            try {
                jobExecutor.execute(() -> processRecord(record, jobId, dispatchGeneration));
                submitted++;
            } catch (TaskRejectedException exception) {
                log.warn("Capture worker capacity is full; message {} remains pending", record.getId());
            }
        }
        return submitted;
    }

    private int availableCapacity() {
        int executionSlots = Math.max(0, jobExecutor.getMaxPoolSize() - jobExecutor.getActiveCount());
        int queueSlots = jobExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
        return Math.min(properties.batchSize(), executionSlots + queueSlots);
    }

    private void processRecord(
            MapRecord<String, String, String> record,
            String jobId,
            String dispatchGeneration
    ) {
        UUID parsedJobId;
        int parsedDispatchGeneration;
        try {
            parsedJobId = UUID.fromString(jobId);
            parsedDispatchGeneration = Integer.parseInt(dispatchGeneration);
            if (parsedDispatchGeneration < 1) {
                throw new IllegalArgumentException("dispatch generation must be positive");
            }
        } catch (IllegalArgumentException exception) {
            log.warn("Discarding malformed capture job message {}", record.getId());
            messageFinalizer.acknowledgeAndDelete(record.getId());
            return;
        }

        Map<String, String> context = messageContext(
                record,
                parsedJobId.toString(),
                Integer.toString(parsedDispatchGeneration)
        );
        try (MdcContextScope ignored = MdcContextScope.replace(context)) {
            try {
                if (processor.process(
                        parsedJobId,
                        parsedDispatchGeneration,
                        properties.consumerName()
                )) {
                    messageFinalizer.acknowledgeAndDelete(record.getId());
                }
            } catch (RuntimeException exception) {
                log.error("Capture job message {} could not be processed", record.getId(), exception);
            }
        }
    }

    private Map<String, String> messageContext(
            MapRecord<String, String, String> record,
            String jobId,
            String dispatchGeneration
    ) {
        Map<String, String> context = new HashMap<>();
        context.put("jobId", jobId);
        context.put("dispatchGeneration", dispatchGeneration);
        context.put("workerId", properties.consumerName());
        putUuid(context, "eventId", record.getValue().get("eventId"));
        putUuid(context, "captureId", record.getValue().get("captureId"));
        String originRequestId = record.getValue().get("originRequestId");
        if (!RequestIdPolicy.isValid(originRequestId)) {
            originRequestId = context.getOrDefault("eventId", jobId);
        }
        context.put("requestId", originRequestId);
        return Map.copyOf(context);
    }

    private void putUuid(Map<String, String> context, String key, String value) {
        if (value == null) {
            return;
        }
        try {
            context.put(key, UUID.fromString(value).toString());
        } catch (IllegalArgumentException ignored) {
            // Optional correlation metadata must never prevent a durable job from running.
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

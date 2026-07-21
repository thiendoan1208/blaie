package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.CaptureJobProcessor;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisCaptureJobWorkerTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void slowAiJobDoesNotBlockRecoveryScheduler() throws Exception {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setConsumerName("worker-test");
        ThreadPoolTaskExecutor executor = executor(properties);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock();
        PendingMessages pending = mock(PendingMessages.class);
        CaptureJobProcessor processor = mock(CaptureJobProcessor.class);
        RedisCaptureMessageFinalizer messageFinalizer = mock(RedisCaptureMessageFinalizer.class);
        UUID jobId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID captureId = UUID.randomUUID();
        MapRecord<String, String, String> record = StreamRecords
                .mapBacked(Map.of(
                        "eventId", eventId.toString(),
                        "jobId", jobId.toString(),
                        "captureId", captureId.toString(),
                        "originRequestId", "worker-request-123",
                        "dispatchGeneration", "3"
                ))
                .withStreamKey(properties.streamKey());
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicReference<Map<String, String>> workerMdc = new AtomicReference<>();
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.add(anyString(), org.mockito.ArgumentMatchers.<Map<String, String>>any()))
                .thenReturn(RecordId.of("1-0"));
        when(streams.pending(
                properties.streamKey(),
                properties.consumerGroup(),
                Range.unbounded(),
                1,
                properties.leaseDuration()
        )).thenReturn(pending);
        when(pending.isEmpty()).thenReturn(true);
        when(streams.read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                org.mockito.ArgumentMatchers.<StreamOffset<String>[]>any()
        )).thenReturn(List.of(record));
        when(processor.process(jobId, 3, properties.consumerName())).thenAnswer(invocation -> {
            workerMdc.set(MDC.getCopyOfContextMap());
            providerStarted.countDown();
            releaseProvider.await();
            return true;
        });
        RedisCaptureJobWorker worker = new RedisCaptureJobWorker(
                redisTemplate,
                properties,
                processor,
                messageFinalizer,
                executor
        );

        try {
            assertTimeout(Duration.ofMillis(500), worker::poll);
            assertThat(providerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(workerMdc.get()).containsAllEntriesOf(Map.of(
                    "requestId", "worker-request-123",
                    "eventId", eventId.toString(),
                    "jobId", jobId.toString(),
                    "captureId", captureId.toString(),
                    "dispatchGeneration", "3",
                    "workerId", properties.consumerName()
            ));

            ProcessingJobStorePort jobStore = mock(ProcessingJobStorePort.class);
            when(jobStore.recoverStale(NOW)).thenReturn(List.of());
            CaptureJobRecoveryScheduler recovery = new CaptureJobRecoveryScheduler(
                    jobStore,
                    mock(IncompleteEventPublications.class),
                    properties,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    mock(CaptureTelemetryPort.class)
            );
            assertTimeout(Duration.ofMillis(200), recovery::recoverJobs);
            verify(jobStore).recoverStale(NOW);
        } finally {
            releaseProvider.countDown();
            executor.shutdown();
        }

        verify(messageFinalizer, timeout(1_000)).acknowledgeAndDelete(record.getId());
    }

    @Test
    void legacyMessageWithoutCorrelationMetadataStillRunsWithSafeJobFallback() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setConsumerName("legacy-worker-test");
        ThreadPoolTaskExecutor executor = executor(properties);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock();
        PendingMessages pending = mock(PendingMessages.class);
        CaptureJobProcessor processor = mock(CaptureJobProcessor.class);
        RedisCaptureMessageFinalizer messageFinalizer = mock(RedisCaptureMessageFinalizer.class);
        UUID jobId = UUID.randomUUID();
        MapRecord<String, String, String> record = StreamRecords
                .mapBacked(Map.of(
                        "jobId", jobId.toString(),
                        "dispatchGeneration", "1",
                        "eventId", "not-a-uuid",
                        "captureId", "also-not-a-uuid",
                        "originRequestId", "unsafe request id"
                ))
                .withStreamKey(properties.streamKey());
        AtomicReference<Map<String, String>> workerMdc = new AtomicReference<>();
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.add(anyString(), org.mockito.ArgumentMatchers.<Map<String, String>>any()))
                .thenReturn(RecordId.of("1-0"));
        when(streams.pending(
                properties.streamKey(),
                properties.consumerGroup(),
                Range.unbounded(),
                1,
                properties.leaseDuration()
        )).thenReturn(pending);
        when(pending.isEmpty()).thenReturn(true);
        when(streams.read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                org.mockito.ArgumentMatchers.<StreamOffset<String>[]>any()
        )).thenReturn(List.of(record));
        when(processor.process(jobId, 1, properties.consumerName())).thenAnswer(invocation -> {
            workerMdc.set(MDC.getCopyOfContextMap());
            return true;
        });
        RedisCaptureJobWorker worker = new RedisCaptureJobWorker(
                redisTemplate,
                properties,
                processor,
                messageFinalizer,
                executor
        );

        try {
            worker.poll();
            verify(messageFinalizer, timeout(1_000)).acknowledgeAndDelete(record.getId());
            assertThat(workerMdc.get().get("requestId")).isEqualTo(jobId.toString());
            assertThat(workerMdc.get()).doesNotContainKeys("eventId", "captureId");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shutdownStopsPollingBeforeRedisIsTouched() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        ThreadPoolTaskExecutor executor = executor(properties);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisCaptureJobWorker worker = new RedisCaptureJobWorker(
                redisTemplate,
                properties,
                mock(CaptureJobProcessor.class),
                mock(RedisCaptureMessageFinalizer.class),
                executor
        );

        worker.stopAcceptingWork();
        worker.poll();

        verifyNoInteractions(redisTemplate);
        executor.shutdown();
    }

    private ThreadPoolTaskExecutor executor(CaptureProcessingProperties properties) {
        ThreadPoolTaskExecutor executor = new CaptureAsyncConfiguration().captureJobExecutor(
                properties,
                mock(TaskScheduler.class)
        );
        executor.initialize();
        return executor;
    }
}

package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.CaptureJobProcessor;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisCaptureJobWorkerTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void slowAiJobDoesNotBlockRecoveryScheduler() throws Exception {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setConsumerName("worker-test");
        ThreadPoolTaskExecutor executor = executor(properties);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        PendingMessages pending = mock(PendingMessages.class);
        CaptureJobProcessor processor = mock(CaptureJobProcessor.class);
        RedisCaptureMessageFinalizer messageFinalizer = mock(RedisCaptureMessageFinalizer.class);
        UUID jobId = UUID.randomUUID();
        MapRecord<String, String, String> record = StreamRecords
                .mapBacked(Map.of(
                        "jobId", jobId.toString(),
                        "dispatchGeneration", "3"
                ))
                .withStreamKey(properties.streamKey());
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        doReturn(streams).when(redisTemplate).opsForStream();
        when(streams.add(anyString(), any(Map.class))).thenReturn(RecordId.of("1-0"));
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
                any(StreamOffset[].class)
        )).thenReturn(List.of(record));
        when(processor.process(jobId, 3, properties.consumerName())).thenAnswer(invocation -> {
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

            ProcessingJobStorePort jobStore = mock(ProcessingJobStorePort.class);
            when(jobStore.recoverStale(NOW)).thenReturn(List.of());
            CaptureJobRecoveryScheduler recovery = new CaptureJobRecoveryScheduler(
                    jobStore,
                    mock(IncompleteEventPublications.class),
                    properties,
                    Clock.fixed(NOW, ZoneOffset.UTC)
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

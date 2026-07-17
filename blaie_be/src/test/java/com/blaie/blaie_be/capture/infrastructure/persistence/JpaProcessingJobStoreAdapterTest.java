package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JpaProcessingJobStoreAdapterTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void staleRecoveryUsesBackoffForEachJobsCurrentAttempt() {
        CaptureEntity firstCapture = CaptureEntity.processing(UUID.randomUUID(), "First");
        ProcessingJobEntity firstAttempt = processingJob(firstCapture, 1, 4);
        CaptureEntity thirdCapture = CaptureEntity.processing(UUID.randomUUID(), "Third");
        ProcessingJobEntity thirdAttempt = processingJob(thirdCapture, 3, 4);
        Fixture fixture = fixture(List.of(firstAttempt, thirdAttempt));
        when(fixture.captureRepository.findLockedById(firstCapture.id())).thenReturn(Optional.of(firstCapture));
        when(fixture.captureRepository.findLockedById(thirdCapture.id())).thenReturn(Optional.of(thirdCapture));

        fixture.adapter.recoverStale(NOW);

        assertThat(firstAttempt.status()).isEqualTo("retry_wait");
        assertThat(firstAttempt.availableAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(thirdAttempt.status()).isEqualTo("retry_wait");
        assertThat(thirdAttempt.availableAt()).isEqualTo(NOW.plusSeconds(45));
    }

    @Test
    void staleFinalAttemptMarksJobAndCaptureFailed() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "Final");
        ProcessingJobEntity finalAttempt = processingJob(capture, 4, 4);
        Fixture fixture = fixture(List.of(finalAttempt));
        when(fixture.captureRepository.findLockedById(capture.id())).thenReturn(Optional.of(capture));

        fixture.adapter.recoverStale(NOW);

        assertThat(finalAttempt.status()).isEqualTo("dead");
        assertThat(finalAttempt.lastFailureClass())
                .isEqualTo(TextClassificationFailureClass.SYSTEM_RETRYABLE);
        assertThat(finalAttempt.manualRetryAllowed()).isTrue();
        assertThat(capture.processingStatus()).isEqualTo("failed");
        assertThat(capture.failureCode()).isEqualTo("job_lease_expired");
    }

    @Test
    void staleQueuedJobIsRedispatchedWithIncreasingGenerationAndCadence() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "Lost Redis message");
        ProcessingJobEntity job = ProcessingJobEntity.queued(
                capture,
                4,
                NOW.minusSeconds(30),
                NOW
        );
        Fixture fixture = fixture(List.of());
        when(fixture.jobRepository.findDueDispatches(eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(job));

        assertThat(fixture.adapter.redispatchStaleQueued(NOW, 10)).isEqualTo(1);
        assertThat(job.dispatchGeneration()).isEqualTo(2);
        assertThat(job.lastDispatchedAt()).isEqualTo(NOW);
        assertThat(job.nextDispatchAt()).isEqualTo(NOW.plusSeconds(120));

        Instant secondRedispatch = NOW.plusSeconds(120);
        when(fixture.jobRepository.findDueDispatches(eq(secondRedispatch), any(Pageable.class)))
                .thenReturn(List.of(job));

        assertThat(fixture.adapter.redispatchStaleQueued(secondRedispatch, 10)).isEqualTo(1);
        assertThat(job.dispatchGeneration()).isEqualTo(3);
        assertThat(job.lastDispatchedAt()).isEqualTo(secondRedispatch);
        assertThat(job.nextDispatchAt()).isEqualTo(secondRedispatch.plusSeconds(600));

        ArgumentCaptor<TextCaptureQueuedEvent> events = ArgumentCaptor.forClass(TextCaptureQueuedEvent.class);
        verify(fixture.eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues())
                .extracting(TextCaptureQueuedEvent::dispatchGeneration)
                .containsExactly(2, 3);
        assertThat(events.getAllValues())
                .allSatisfy(event -> {
                    assertThat(event.jobId()).isEqualTo(job.id());
                    assertThat(event.captureId()).isEqualTo(capture.id());
                });
    }

    @Test
    void staleWorkerResultCannotCompleteANewerAttempt() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "Slow provider response");
        ProcessingJobEntity job = ProcessingJobEntity.queued(capture, 4, NOW, NOW.plusSeconds(30));
        assertThat(job.claim(1, "worker-1", NOW, NOW.plusSeconds(30))).isTrue();
        job.scheduleRetry(
                "job_lease_expired",
                TextClassificationFailureClass.SYSTEM_RETRYABLE,
                NOW.plusSeconds(31)
        );
        job.dispatch(NOW.plusSeconds(31), NOW.plusSeconds(61));
        assertThat(job.claim(2, "worker-2", NOW.plusSeconds(31), NOW.plusSeconds(61))).isTrue();
        Fixture fixture = fixture(List.of());
        when(fixture.jobRepository.findLockedById(job.id())).thenReturn(Optional.of(job));
        CaptureAnalysis staleAnalysis = new CaptureAnalysis(List.of(), "test", "model", "v1");

        boolean completed = fixture.adapter.complete(
                job.id(),
                "worker-1",
                1,
                0,
                staleAnalysis,
                NOW.plusSeconds(32)
        );

        assertThat(completed).isFalse();
        assertThat(job.status()).isEqualTo("processing");
        assertThat(job.ownsLease("worker-2", 2, 0)).isTrue();
        assertThat(capture.processingStatus()).isEqualTo("processing");
        verifyNoInteractions(fixture.itemRepository);
    }

    private ProcessingJobEntity processingJob(CaptureEntity capture, int attempts, int maxAttempts) {
        Instant firstDispatch = NOW.minusSeconds(300);
        ProcessingJobEntity job = ProcessingJobEntity.queued(
                capture,
                maxAttempts,
                firstDispatch,
                firstDispatch.plusSeconds(30)
        );
        for (int attempt = 1; attempt <= attempts; attempt++) {
            assertThat(job.claim(
                    job.dispatchGeneration(),
                    "worker-" + attempt,
                    NOW.minusSeconds(40),
                    NOW.minusSeconds(30)
            )).isTrue();
            if (attempt < attempts) {
                job.scheduleRetry(
                        "ai_provider_unavailable",
                        TextClassificationFailureClass.PROVIDER_RETRYABLE,
                        NOW.minusSeconds(50)
                );
                job.dispatch(NOW.minusSeconds(50), NOW.minusSeconds(20));
            }
        }
        return job;
    }

    private Fixture fixture(List<ProcessingJobEntity> staleJobs) {
        ProcessingJobRepository jobRepository = mock(ProcessingJobRepository.class);
        CaptureRepository captureRepository = mock(CaptureRepository.class);
        CaptureItemRepository itemRepository = mock(CaptureItemRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(jobRepository.findStale(eq(NOW), any(Pageable.class))).thenReturn(staleJobs);
        CaptureProcessingSettingsPort settings = new TestSettings();
        JpaProcessingJobStoreAdapter adapter = new JpaProcessingJobStoreAdapter(
                jobRepository,
                captureRepository,
                itemRepository,
                eventPublisher,
                settings
        );
        return new Fixture(adapter, jobRepository, captureRepository, itemRepository, eventPublisher);
    }

    private record Fixture(
            JpaProcessingJobStoreAdapter adapter,
            ProcessingJobRepository jobRepository,
            CaptureRepository captureRepository,
            CaptureItemRepository itemRepository,
            ApplicationEventPublisher eventPublisher
    ) {
    }

    private static final class TestSettings implements CaptureProcessingSettingsPort {
        @Override
        public boolean acceptAsyncEnabled() {
            return true;
        }

        @Override
        public int maxAttempts() {
            return 4;
        }

        @Override
        public Duration idempotencyTtl() {
            return Duration.ofHours(24);
        }

        @Override
        public Duration leaseDuration() {
            return Duration.ofSeconds(30);
        }

        @Override
        public Duration heartbeatInterval() {
            return Duration.ofSeconds(10);
        }

        @Override
        public Duration retryDelay(int failedAttemptCount) {
            return switch (failedAttemptCount) {
                case 1 -> Duration.ofSeconds(5);
                case 2 -> Duration.ofSeconds(15);
                default -> Duration.ofSeconds(45);
            };
        }

        @Override
        public Duration dispatchRetryDelay(int dispatchGeneration) {
            return switch (dispatchGeneration) {
                case 1 -> Duration.ofSeconds(30);
                case 2 -> Duration.ofSeconds(120);
                default -> Duration.ofSeconds(600);
            };
        }
    }
}

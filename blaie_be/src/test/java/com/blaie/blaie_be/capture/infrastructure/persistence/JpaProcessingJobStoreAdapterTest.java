package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        assertThat(capture.processingStatus()).isEqualTo("failed");
        assertThat(capture.failureCode()).isEqualTo("job_lease_expired");
    }

    private ProcessingJobEntity processingJob(CaptureEntity capture, int attempts, int maxAttempts) {
        ProcessingJobEntity job = ProcessingJobEntity.queued(capture, maxAttempts, NOW.minusSeconds(300));
        for (int attempt = 1; attempt <= attempts; attempt++) {
            assertThat(job.claim(
                    "worker-" + attempt,
                    NOW.minusSeconds(40),
                    NOW.minusSeconds(30)
            )).isTrue();
            if (attempt < attempts) {
                job.scheduleRetry("provider_unavailable", NOW.minusSeconds(50));
                job.dispatch();
            }
        }
        return job;
    }

    private Fixture fixture(List<ProcessingJobEntity> staleJobs) {
        ProcessingJobRepository jobRepository = mock(ProcessingJobRepository.class);
        CaptureRepository captureRepository = mock(CaptureRepository.class);
        CaptureItemRepository itemRepository = mock(CaptureItemRepository.class);
        when(jobRepository.findStale(eq(NOW), any(Pageable.class))).thenReturn(staleJobs);
        CaptureProcessingSettingsPort settings = new TestSettings();
        JpaProcessingJobStoreAdapter adapter = new JpaProcessingJobStoreAdapter(
                jobRepository,
                captureRepository,
                itemRepository,
                mock(ApplicationEventPublisher.class),
                settings
        );
        return new Fixture(adapter, captureRepository);
    }

    private record Fixture(
            JpaProcessingJobStoreAdapter adapter,
            CaptureRepository captureRepository
    ) {
    }

    private static final class TestSettings implements CaptureProcessingSettingsPort {
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
    }
}

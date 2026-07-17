package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureJobProcessorTest {
    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    @Test
    void successfulJobCompletesExactlyOnce() {
        FakeJobStore store = new FakeJobStore(job(1, 4));
        CaptureAnalysis analysis = new CaptureAnalysis(List.of(), "test", "model", "v1");
        CaptureJobProcessor processor = processor(store, text -> analysis);

        assertThat(processor.process(store.job.id(), "worker-1")).isTrue();
        assertThat(store.completedAnalysis).isSameAs(analysis);
        assertThat(store.retryAt).isNull();
        assertThat(store.deadError).isNull();

        store.claimed = Optional.empty();
        assertThat(processor.process(store.job.id(), "worker-2")).isTrue();
        assertThat(store.completeCalls).isEqualTo(1);
    }

    @Test
    void transientFailureSchedulesBackoffWhileAttemptsRemain() {
        FakeJobStore store = new FakeJobStore(job(1, 4));
        CaptureJobProcessor processor = processor(store, text -> {
            throw new TextClassificationException("ai_provider_unavailable", "safe internal detail");
        });

        assertThat(processor.process(store.job.id(), "worker-1")).isTrue();
        assertThat(store.retryError).isEqualTo("ai_provider_unavailable");
        assertThat(store.retryAt).isEqualTo(NOW.plusSeconds(2));
        assertThat(store.deadError).isNull();
    }

    @Test
    void finalFailureMarksJobDead() {
        FakeJobStore store = new FakeJobStore(job(4, 4));
        CaptureJobProcessor processor = processor(store, text -> {
            throw new TextClassificationException("ai_invalid_response", "invalid response");
        });

        assertThat(processor.process(store.job.id(), "worker-1")).isTrue();
        assertThat(store.deadError).isEqualTo("ai_invalid_response");
        assertThat(store.retryAt).isNull();
    }

    private CaptureJobProcessor processor(FakeJobStore store, TextClassifierPort classifier) {
        CaptureProcessingSettingsPort settings = new CaptureProcessingSettingsPort() {
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
            public Duration retryDelay(int failedAttemptCount) {
                return Duration.ofSeconds(2);
            }
        };
        return new CaptureJobProcessor(
                store,
                classifier,
                new CaptureContentPolicy(),
                settings,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ProcessingJobResult job(int attemptCount, int maxAttempts) {
        return new ProcessingJobResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Buy milk",
                ProcessingJobStatus.PROCESSING,
                attemptCount,
                maxAttempts,
                0,
                NOW
        );
    }

    private static final class FakeJobStore implements ProcessingJobStorePort {
        private final ProcessingJobResult job;
        private Optional<ProcessingJobResult> claimed;
        private CaptureAnalysis completedAnalysis;
        private int completeCalls;
        private String retryError;
        private Instant retryAt;
        private String deadError;

        private FakeJobStore(ProcessingJobResult job) {
            this.job = job;
            this.claimed = Optional.of(job);
        }

        @Override
        public Optional<ProcessingJobResult> claim(UUID jobId, String workerId, Instant now, Instant leaseExpiresAt) {
            return claimed;
        }

        @Override
        public void complete(UUID jobId, CaptureAnalysis analysis, Instant now) {
            completedAnalysis = analysis;
            completeCalls++;
        }

        @Override
        public void scheduleRetry(UUID jobId, String errorCode, Instant availableAt, Instant now) {
            retryError = errorCode;
            retryAt = availableAt;
        }

        @Override
        public void markDead(UUID jobId, String errorCode, Instant now) {
            deadError = errorCode;
        }

        @Override
        public List<RecoveredJobResult> recoverStale(Instant now, Duration retryDelay) {
            return List.of();
        }

        @Override
        public int dispatchReadyRetries(Instant now, int limit) {
            return 0;
        }
    }
}

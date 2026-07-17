package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.JobLeaseHeartbeatPort;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
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
        FakeHeartbeat heartbeat = new FakeHeartbeat();
        CaptureJobProcessor processor = processor(store, text -> analysis, heartbeat);

        assertThat(processor.process(store.job.id(), store.job.dispatchGeneration(), "worker-1")).isTrue();
        assertThat(store.completedAnalysis).isSameAs(analysis);
        assertThat(store.retryAt).isNull();
        assertThat(store.deadError).isNull();
        assertThat(store.claimDispatchGeneration).isEqualTo(store.job.dispatchGeneration());
        assertThat(store.completedWorkerId).isEqualTo("worker-1");
        assertThat(store.completedAttemptCount).isEqualTo(store.job.attemptCount());
        assertThat(store.completedRetryGeneration).isEqualTo(store.job.retryGeneration());
        assertThat(heartbeat.startedJobId).isEqualTo(store.job.id());
        assertThat(heartbeat.startedWorkerId).isEqualTo("worker-1");
        assertThat(heartbeat.startedAttemptCount).isEqualTo(store.job.attemptCount());
        assertThat(heartbeat.startedRetryGeneration).isEqualTo(store.job.retryGeneration());
        assertThat(heartbeat.stopCalls).isEqualTo(1);

        store.claimed = Optional.empty();
        assertThat(processor.process(store.job.id(), store.job.dispatchGeneration(), "worker-2")).isTrue();
        assertThat(store.completeCalls).isEqualTo(1);
    }

    @Test
    void transientFailureSchedulesBackoffWhileAttemptsRemain() {
        FakeJobStore store = new FakeJobStore(job(1, 4));
        CaptureJobProcessor processor = processor(store, text -> {
            throw new TextClassificationException(
                    "ai_provider_unavailable",
                    "safe internal detail",
                    TextClassificationFailureClass.PROVIDER_RETRYABLE
            );
        }, new FakeHeartbeat());

        assertThat(processor.process(store.job.id(), store.job.dispatchGeneration(), "worker-1")).isTrue();
        assertThat(store.retryError).isEqualTo("ai_provider_unavailable");
        assertThat(store.retryFailureClass)
                .isEqualTo(TextClassificationFailureClass.PROVIDER_RETRYABLE);
        assertThat(store.retryAt).isEqualTo(NOW.plusSeconds(2));
        assertThat(store.retryWorkerId).isEqualTo("worker-1");
        assertThat(store.retryAttemptCount).isEqualTo(store.job.attemptCount());
        assertThat(store.retryGeneration).isEqualTo(store.job.retryGeneration());
        assertThat(store.deadError).isNull();
    }

    @Test
    void finalFailureMarksJobDead() {
        FakeJobStore store = new FakeJobStore(job(4, 4));
        CaptureJobProcessor processor = processor(store, text -> {
            throw new TextClassificationException(
                    "ai_invalid_response",
                    "invalid response",
                    TextClassificationFailureClass.PROVIDER_RETRYABLE
            );
        }, new FakeHeartbeat());

        assertThat(processor.process(store.job.id(), store.job.dispatchGeneration(), "worker-1")).isTrue();
        assertThat(store.deadError).isEqualTo("ai_invalid_response");
        assertThat(store.deadFailureClass)
                .isEqualTo(TextClassificationFailureClass.PROVIDER_RETRYABLE);
        assertThat(store.deadWorkerId).isEqualTo("worker-1");
        assertThat(store.deadAttemptCount).isEqualTo(store.job.attemptCount());
        assertThat(store.deadRetryGeneration).isEqualTo(store.job.retryGeneration());
        assertThat(store.retryAt).isNull();
    }

    @Test
    void providerTerminalFailureStopsAutomaticRetriesButAllowsPolicyAwareManualRetry() {
        FakeJobStore store = new FakeJobStore(job(1, 4));
        CaptureJobProcessor processor = processor(store, text -> {
            throw new TextClassificationException(
                    "ai_provider_rejected",
                    "provider configuration was rejected",
                    TextClassificationFailureClass.PROVIDER_TERMINAL
            );
        }, new FakeHeartbeat());

        assertThat(processor.process(store.job.id(), store.job.dispatchGeneration(), "worker-1")).isTrue();
        assertThat(store.deadError).isEqualTo("ai_provider_rejected");
        assertThat(store.deadFailureClass)
                .isEqualTo(TextClassificationFailureClass.PROVIDER_TERMINAL);
        assertThat(store.deadFailureClass.manualRetryAllowed()).isTrue();
        assertThat(store.retryAt).isNull();
    }

    @Test
    void credentialPolicyFailureIsTerminalAndCannotBeManuallyRetried() {
        FakeJobStore store = new FakeJobStore(job(
                "save sk-abcdefghijklmnopqrstuvwxyz123456",
                1,
                4
        ));
        CaptureJobProcessor processor = processor(
                store,
                text -> {
                    throw new AssertionError("classifier must not receive credential-like text");
                },
                new FakeHeartbeat()
        );

        assertThat(processor.process(
                store.job.id(),
                store.job.dispatchGeneration(),
                "worker-1"
        )).isTrue();
        assertThat(store.deadError).isEqualTo("sensitive_credential_detected");
        assertThat(store.deadFailureClass)
                .isEqualTo(TextClassificationFailureClass.CONTENT_TERMINAL);
        assertThat(store.deadFailureClass.manualRetryAllowed()).isFalse();
        assertThat(store.retryAt).isNull();
    }

    @Test
    void invalidFailureCodeFallsBackToSafeSystemRetryableFailure() {
        FakeJobStore store = new FakeJobStore(job(1, 4));
        CaptureJobProcessor processor = processor(store, text -> {
            throw new TextClassificationException(
                    "UNSAFE PROVIDER ERROR",
                    "unsafe detail",
                    TextClassificationFailureClass.CONTENT_TERMINAL
            );
        }, new FakeHeartbeat());

        assertThat(processor.process(store.job.id(), store.job.dispatchGeneration(), "worker-1")).isTrue();
        assertThat(store.retryError).isEqualTo("unexpected_classification_error");
        assertThat(store.retryFailureClass)
                .isEqualTo(TextClassificationFailureClass.SYSTEM_RETRYABLE);
        assertThat(store.deadError).isNull();
    }

    @Test
    void staleSuccessfulResultIsDiscardedWithoutMutatingNewerAttempt() {
        FakeJobStore store = new FakeJobStore(job(1, 4));
        store.completeAccepted = false;
        CaptureAnalysis analysis = new CaptureAnalysis(List.of(), "test", "model", "v1");
        FakeHeartbeat heartbeat = new FakeHeartbeat();
        CaptureJobProcessor processor = processor(store, text -> analysis, heartbeat);

        assertThat(processor.process(store.job.id(), store.job.dispatchGeneration(), "stale-worker")).isTrue();

        assertThat(store.completeCalls).isEqualTo(1);
        assertThat(store.completedWorkerId).isEqualTo("stale-worker");
        assertThat(store.retryAt).isNull();
        assertThat(store.deadError).isNull();
        assertThat(heartbeat.stopCalls).isEqualTo(1);
    }

    private CaptureJobProcessor processor(
            FakeJobStore store,
            TextClassifierPort classifier,
            JobLeaseHeartbeatPort heartbeat
    ) {
        CaptureProcessingSettingsPort settings = new CaptureProcessingSettingsPort() {
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
                return Duration.ofSeconds(2);
            }

            @Override
            public Duration dispatchRetryDelay(int dispatchGeneration) {
                return Duration.ofSeconds(30);
            }

            @Override
            public int maxActiveJobsPerUser() {
                return 10;
            }

            @Override
            public int maxActiveJobsTotal() {
                return 1_000;
            }

            @Override
            public Duration maxOldestQueuedAge() {
                return Duration.ofMinutes(5);
            }

            @Override
            public Duration admissionRetryAfter() {
                return Duration.ofSeconds(30);
            }
        };
        return new CaptureJobProcessor(
                store,
                classifier,
                new CaptureContentPolicy(),
                settings,
                Clock.fixed(NOW, ZoneOffset.UTC),
                heartbeat
        );
    }

    private ProcessingJobResult job(int attemptCount, int maxAttempts) {
        return job("Buy milk", attemptCount, maxAttempts);
    }

    private ProcessingJobResult job(String originalText, int attemptCount, int maxAttempts) {
        return new ProcessingJobResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                originalText,
                ProcessingJobStatus.PROCESSING,
                attemptCount,
                maxAttempts,
                2,
                7,
                NOW
        );
    }

    private static final class FakeJobStore implements ProcessingJobStorePort {
        private final ProcessingJobResult job;
        private Optional<ProcessingJobResult> claimed;
        private CaptureAnalysis completedAnalysis;
        private int completeCalls;
        private boolean completeAccepted = true;
        private int claimDispatchGeneration;
        private String completedWorkerId;
        private int completedAttemptCount;
        private int completedRetryGeneration;
        private String retryError;
        private TextClassificationFailureClass retryFailureClass;
        private Instant retryAt;
        private String retryWorkerId;
        private int retryAttemptCount;
        private int retryGeneration;
        private String deadError;
        private TextClassificationFailureClass deadFailureClass;
        private String deadWorkerId;
        private int deadAttemptCount;
        private int deadRetryGeneration;

        private FakeJobStore(ProcessingJobResult job) {
            this.job = job;
            this.claimed = Optional.of(job);
        }

        @Override
        public Optional<ProcessingJobResult> claim(
                UUID jobId,
                int dispatchGeneration,
                String workerId,
                Instant now,
                Instant leaseExpiresAt
        ) {
            claimDispatchGeneration = dispatchGeneration;
            return claimed;
        }

        @Override
        public boolean extendLease(
                UUID jobId,
                String workerId,
                int attemptCount,
                int retryGeneration,
                Instant leaseExpiresAt
        ) {
            return true;
        }

        @Override
        public boolean complete(
                UUID jobId,
                String workerId,
                int attemptCount,
                int retryGeneration,
                CaptureAnalysis analysis,
                Instant now
        ) {
            completedAnalysis = analysis;
            completedWorkerId = workerId;
            completedAttemptCount = attemptCount;
            completedRetryGeneration = retryGeneration;
            completeCalls++;
            return completeAccepted;
        }

        @Override
        public boolean scheduleRetry(
                UUID jobId,
                String workerId,
                int attemptCount,
                int retryGeneration,
                String errorCode,
                TextClassificationFailureClass failureClass,
                Instant availableAt,
                Instant now
        ) {
            retryWorkerId = workerId;
            retryAttemptCount = attemptCount;
            this.retryGeneration = retryGeneration;
            retryError = errorCode;
            retryFailureClass = failureClass;
            retryAt = availableAt;
            return true;
        }

        @Override
        public boolean markDead(
                UUID jobId,
                String workerId,
                int attemptCount,
                int retryGeneration,
                String errorCode,
                TextClassificationFailureClass failureClass,
                Instant now
        ) {
            deadWorkerId = workerId;
            deadAttemptCount = attemptCount;
            deadRetryGeneration = retryGeneration;
            deadError = errorCode;
            deadFailureClass = failureClass;
            return true;
        }

        @Override
        public List<RecoveredJobResult> recoverStale(Instant now) {
            return List.of();
        }

        @Override
        public int dispatchReadyRetries(Instant now, int limit) {
            return 0;
        }

        @Override
        public int redispatchStaleQueued(Instant now, int limit) {
            return 0;
        }
    }

    private static final class FakeHeartbeat implements JobLeaseHeartbeatPort {
        private UUID startedJobId;
        private String startedWorkerId;
        private int startedAttemptCount;
        private int startedRetryGeneration;
        private int stopCalls;

        @Override
        public ActiveHeartbeat start(
                UUID jobId,
                String workerId,
                int attemptCount,
                int retryGeneration
        ) {
            startedJobId = jobId;
            startedWorkerId = workerId;
            startedAttemptCount = attemptCount;
            startedRetryGeneration = retryGeneration;
            return () -> stopCalls++;
        }
    }
}

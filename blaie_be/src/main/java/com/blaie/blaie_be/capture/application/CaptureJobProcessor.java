package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.DeadSource;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.JobOutcome;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.RetrySource;
import com.blaie.blaie_be.capture.application.port.JobLeaseHeartbeatPort;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import com.blaie.blaie_be.core.request.MdcContextScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CaptureJobProcessor {
    private static final Logger log = LoggerFactory.getLogger(CaptureJobProcessor.class);
    private static final String UNEXPECTED_ERROR = "unexpected_classification_error";

    private final ProcessingJobStorePort jobStore;
    private final TextClassifierPort classifier;
    private final CaptureContentPolicy contentPolicy;
    private final CapturePiiPolicy piiPolicy;
    private final CaptureProcessingSettingsPort settings;
    private final Clock clock;
    private final JobLeaseHeartbeatPort heartbeatPort;
    private final CaptureTelemetryPort telemetry;

    public CaptureJobProcessor(
            ProcessingJobStorePort jobStore,
            TextClassifierPort classifier,
            CaptureContentPolicy contentPolicy,
            CapturePiiPolicy piiPolicy,
            CaptureProcessingSettingsPort settings,
            Clock clock,
            JobLeaseHeartbeatPort heartbeatPort,
            CaptureTelemetryPort telemetry
    ) {
        this.jobStore = jobStore;
        this.classifier = classifier;
        this.contentPolicy = contentPolicy;
        this.piiPolicy = piiPolicy;
        this.settings = settings;
        this.clock = clock;
        this.heartbeatPort = heartbeatPort;
        this.telemetry = telemetry;
    }

    public boolean process(UUID jobId, int dispatchGeneration, String workerId) {
        Instant claimedAt = clock.instant();
        Optional<ProcessingJobResult> claimed = jobStore.claim(
                jobId,
                dispatchGeneration,
                workerId,
                claimedAt,
                claimedAt.plus(settings.leaseDuration())
        );
        if (claimed.isEmpty()) {
            return true;
        }

        ProcessingJobResult job = claimed.get();
        try (MdcContextScope _ = MdcContextScope.overlay(Map.of(
                "requestId", job.originRequestId(),
                "jobId", job.id().toString(),
                "captureId", job.captureId().toString(),
                "attempt", Integer.toString(job.attemptCount()),
                "retryGeneration", Integer.toString(job.retryGeneration()),
                "dispatchGeneration", Integer.toString(job.dispatchGeneration())
        ))) {
            return processClaimed(job, workerId);
        }
    }

    private boolean processClaimed(ProcessingJobResult job, String workerId) {
        JobLeaseHeartbeatPort.ActiveHeartbeat heartbeat = heartbeatPort.start(
                job.id(),
                workerId,
                job.attemptCount(),
                job.retryGeneration()
        );
        long startedAtNanos = System.nanoTime();
        JobOutcome outcome = JobOutcome.STALE_DISCARDED;
        try {
            contentPolicy.validate(job.originalText());
            CapturePiiPolicy.PreparedText preparedText = piiPolicy.prepare(job.originalText());
            CaptureAnalysis analysis = piiPolicy.restore(
                    preparedText,
                    classifier.classify(preparedText.providerText())
            );
            boolean completed = jobStore.complete(
                    job.id(),
                    workerId,
                    job.attemptCount(),
                    job.retryGeneration(),
                    analysis,
                    clock.instant()
            );
            if (!completed) {
                log.info(
                        "Discarded stale capture result: jobId={}, attempt={}, retryGeneration={}",
                        job.id(), job.attemptCount(), job.retryGeneration()
                );
                return true;
            }
            outcome = JobOutcome.COMPLETED;
            log.info(
                    "Capture job completed: jobId={}, captureId={}, attempt={}, provider={}, model={}, durationMs={}",
                    job.id(),
                    job.captureId(),
                    job.attemptCount(),
                    analysis.provider(),
                    analysis.model(),
                    (System.nanoTime() - startedAtNanos) / 1_000_000
            );
            return true;
        } catch (TextClassificationException exception) {
            Failure failure = safeFailure(exception.failureCode(), exception.failureClass());
            outcome = handleFailure(job, workerId, failure);
            return true;
        } catch (RuntimeException exception) {
            log.error("Unexpected text capture classification failure for job {}", job.id(), exception);
            outcome = handleFailure(job, workerId, new Failure(
                    UNEXPECTED_ERROR,
                    TextClassificationFailureClass.SYSTEM_RETRYABLE
            ));
            return true;
        } finally {
            heartbeat.stop();
            telemetry.recordJobDuration(
                    Duration.ofNanos(System.nanoTime() - startedAtNanos),
                    outcome
            );
        }
    }

    private JobOutcome handleFailure(
            ProcessingJobResult job,
            String workerId,
            Failure failure
    ) {
        Instant now = clock.instant();
        if (!failure.failureClass().automaticRetryAllowed()
                || job.attemptCount() >= job.maxAttempts()) {
            boolean markedDead = jobStore.markDead(
                    job.id(),
                    workerId,
                    job.attemptCount(),
                    job.retryGeneration(),
                    failure.errorCode(),
                    failure.failureClass(),
                    now
            );
            if (!markedDead) {
                log.info("Discarded stale capture failure: jobId={}, attempt={}", job.id(), job.attemptCount());
                return JobOutcome.STALE_DISCARDED;
            }
            telemetry.incrementDead(DeadSource.WORKER, failure.failureClass());
            log.warn(
                    "Capture job marked dead: jobId={}, captureId={}, attempt={}, errorCode={}",
                    job.id(), job.captureId(), job.attemptCount(), failure.errorCode()
            );
            return JobOutcome.DEAD;
        }
        Instant retryAt = now.plus(settings.retryDelay(job.attemptCount()));
        boolean scheduled = jobStore.scheduleRetry(
                job.id(),
                workerId,
                job.attemptCount(),
                job.retryGeneration(),
                failure.errorCode(),
                failure.failureClass(),
                retryAt,
                now
        );
        if (!scheduled) {
            log.info("Discarded stale capture retry: jobId={}, attempt={}", job.id(), job.attemptCount());
            return JobOutcome.STALE_DISCARDED;
        }
        telemetry.incrementRetry(RetrySource.AUTOMATIC);
        log.warn(
                "Capture job retry scheduled: jobId={}, captureId={}, attempt={}, errorCode={}, availableAt={}",
                job.id(), job.captureId(), job.attemptCount(), failure.errorCode(), retryAt
        );
        return JobOutcome.RETRY_SCHEDULED;
    }

    private Failure safeFailure(
            String errorCode,
            TextClassificationFailureClass failureClass
    ) {
        if (errorCode == null
                || !errorCode.matches("[a-z0-9_]{1,100}")
                || failureClass == null) {
            return new Failure(
                    UNEXPECTED_ERROR,
                    TextClassificationFailureClass.SYSTEM_RETRYABLE
            );
        }
        return new Failure(errorCode, failureClass);
    }

    private record Failure(
            String errorCode,
            TextClassificationFailureClass failureClass
    ) {
    }
}

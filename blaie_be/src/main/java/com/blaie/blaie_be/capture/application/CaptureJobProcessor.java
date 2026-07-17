package com.blaie.blaie_be.capture.application;

import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.JobLeaseHeartbeatPort;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import java.time.Clock;
import java.time.Instant;
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
    private final CaptureProcessingSettingsPort settings;
    private final Clock clock;
    private final JobLeaseHeartbeatPort heartbeatPort;

    public CaptureJobProcessor(
            ProcessingJobStorePort jobStore,
            TextClassifierPort classifier,
            CaptureContentPolicy contentPolicy,
            CaptureProcessingSettingsPort settings,
            Clock clock,
            JobLeaseHeartbeatPort heartbeatPort
    ) {
        this.jobStore = jobStore;
        this.classifier = classifier;
        this.contentPolicy = contentPolicy;
        this.settings = settings;
        this.clock = clock;
        this.heartbeatPort = heartbeatPort;
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
        JobLeaseHeartbeatPort.ActiveHeartbeat heartbeat = heartbeatPort.start(
                job.id(),
                workerId,
                job.attemptCount(),
                job.retryGeneration()
        );
        long startedAtNanos = System.nanoTime();
        try {
            contentPolicy.validate(job.originalText());
            CaptureAnalysis analysis = classifier.classify(job.originalText());
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
            handleFailure(job, workerId, safeErrorCode(exception.failureCode()), exception.retryable());
            return true;
        } catch (RuntimeException exception) {
            log.error("Unexpected text capture classification failure for job {}", job.id(), exception);
            handleFailure(job, workerId, UNEXPECTED_ERROR, true);
            return true;
        } finally {
            heartbeat.stop();
        }
    }

    private void handleFailure(
            ProcessingJobResult job,
            String workerId,
            String errorCode,
            boolean retryable
    ) {
        Instant now = clock.instant();
        if (!retryable || job.attemptCount() >= job.maxAttempts()) {
            boolean markedDead = jobStore.markDead(
                    job.id(),
                    workerId,
                    job.attemptCount(),
                    job.retryGeneration(),
                    errorCode,
                    now
            );
            if (!markedDead) {
                log.info("Discarded stale capture failure: jobId={}, attempt={}", job.id(), job.attemptCount());
                return;
            }
            log.warn(
                    "Capture job marked dead: jobId={}, captureId={}, attempt={}, errorCode={}",
                    job.id(), job.captureId(), job.attemptCount(), errorCode
            );
            return;
        }
        Instant retryAt = now.plus(settings.retryDelay(job.attemptCount()));
        boolean scheduled = jobStore.scheduleRetry(
                job.id(),
                workerId,
                job.attemptCount(),
                job.retryGeneration(),
                errorCode,
                retryAt,
                now
        );
        if (!scheduled) {
            log.info("Discarded stale capture retry: jobId={}, attempt={}", job.id(), job.attemptCount());
            return;
        }
        log.warn(
                "Capture job retry scheduled: jobId={}, captureId={}, attempt={}, errorCode={}, availableAt={}",
                job.id(), job.captureId(), job.attemptCount(), errorCode, retryAt
        );
    }

    private String safeErrorCode(String errorCode) {
        if (errorCode == null || !errorCode.matches("[a-z0-9_]{1,100}")) {
            return UNEXPECTED_ERROR;
        }
        return errorCode;
    }
}

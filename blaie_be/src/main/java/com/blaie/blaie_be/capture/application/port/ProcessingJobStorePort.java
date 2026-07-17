package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingJobStorePort {
    Optional<ProcessingJobResult> claim(
            UUID jobId,
            int dispatchGeneration,
            String workerId,
            Instant now,
            Instant leaseExpiresAt
    );

    boolean extendLease(
            UUID jobId,
            String workerId,
            int attemptCount,
            int retryGeneration,
            Instant leaseExpiresAt
    );

    boolean complete(
            UUID jobId,
            String workerId,
            int attemptCount,
            int retryGeneration,
            CaptureAnalysis analysis,
            Instant now
    );

    boolean scheduleRetry(
            UUID jobId,
            String workerId,
            int attemptCount,
            int retryGeneration,
            String errorCode,
            TextClassificationFailureClass failureClass,
            Instant availableAt,
            Instant now
    );

    boolean markDead(
            UUID jobId,
            String workerId,
            int attemptCount,
            int retryGeneration,
            String errorCode,
            TextClassificationFailureClass failureClass,
            Instant now
    );

    List<RecoveredJobResult> recoverStale(Instant now);

    int dispatchReadyRetries(Instant now, int limit);

    int redispatchStaleQueued(Instant now, int limit);
}

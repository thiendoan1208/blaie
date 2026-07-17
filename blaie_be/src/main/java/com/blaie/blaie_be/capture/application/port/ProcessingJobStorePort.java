package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingJobStorePort {
    Optional<ProcessingJobResult> claim(UUID jobId, String workerId, Instant now, Instant leaseExpiresAt);

    boolean extendLease(UUID jobId, String workerId, Instant leaseExpiresAt);

    void complete(UUID jobId, CaptureAnalysis analysis, Instant now);

    void scheduleRetry(UUID jobId, String errorCode, Instant availableAt, Instant now);

    void markDead(UUID jobId, String errorCode, Instant now);

    List<RecoveredJobResult> recoverStale(Instant now);

    int dispatchReadyRetries(Instant now, int limit);
}

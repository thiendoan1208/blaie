package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.application.result.CaptureResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaptureWorkflowStorePort {
    CaptureResult startTextCapture(
            UUID userId,
            String originalText,
            UUID idempotencyKey,
            String requestHash,
            Instant now,
            Instant idempotencyExpiresAt,
            int maxAttempts
    );

    Optional<CaptureResult> findOwned(UUID captureId, UUID userId);

    List<CaptureResult> findOwnedProcessing(UUID userId, int limit);

    CaptureResult retryOwned(UUID captureId, UUID userId, Instant now);
}

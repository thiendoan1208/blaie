package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.application.result.OwnedCaptureAssetResult;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ImageCaptureWorkflowStorePort {
    Optional<CaptureResult> resolveOwnedSubmission(
            UUID userId,
            UUID idempotencyKey,
            String requestHash,
            Instant now
    );

    CaptureResult startImageCapture(
            UUID userId,
            String originalText,
            CaptureAssetDraft asset,
            UUID idempotencyKey,
            String requestHash,
            String originRequestId,
            Instant now,
            Instant idempotencyExpiresAt,
            int maxAttempts
    );

    default Optional<OwnedCaptureAssetResult> findOwnedAsset(
            UUID captureId,
            UUID assetId,
            UUID userId
    ) {
        return Optional.empty();
    }
}

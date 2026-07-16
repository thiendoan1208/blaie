package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaptureItemStorePort {
    CaptureResult createProcessing(UUID userId, String originalText);

    CaptureResult markCompleted(UUID captureId, CaptureAnalysis analysis);

    void markFailed(UUID captureId, String failureCode);

    Optional<CaptureItemResult> findOwned(UUID itemId, UUID userId);

    List<CaptureItemResult> findFirstPage(UUID userId, int limit);

    List<CaptureItemResult> findPageAfter(UUID userId, Instant createdAt, UUID itemId, int limit);
}

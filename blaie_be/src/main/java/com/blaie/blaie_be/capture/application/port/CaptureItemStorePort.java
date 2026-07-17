package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaptureItemStorePort {
    Optional<CaptureItemResult> findOwned(UUID itemId, UUID userId);

    List<CaptureItemResult> findFirstPage(UUID userId, int limit);

    List<CaptureItemResult> findPageAfter(UUID userId, Instant createdAt, UUID itemId, int limit);
}

package com.blaie.blaie_be.capture.application.result;

import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaptureResult(
        UUID id,
        String originalText,
        ProcessingStatus processingStatus,
        List<CaptureItemResult> items,
        Instant createdAt
) {
    public CaptureResult {
        items = List.copyOf(items);
    }
}

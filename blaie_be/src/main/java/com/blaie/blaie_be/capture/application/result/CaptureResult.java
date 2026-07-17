package com.blaie.blaie_be.capture.application.result;

import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaptureResult(
        UUID id,
        String originalText,
        ProcessingStatus processingStatus,
        String failureCode,
        boolean canRetry,
        List<CaptureItemResult> items,
        Instant createdAt,
        Instant updatedAt
) {
    public CaptureResult {
        items = List.copyOf(items);
    }

    public CaptureResult(
            UUID id,
            String originalText,
            ProcessingStatus processingStatus,
            List<CaptureItemResult> items,
            Instant createdAt
    ) {
        this(id, originalText, processingStatus, null, false, items, createdAt, createdAt);
    }
}

package com.blaie.blaie_be.capture.application.result;

import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import java.time.Instant;
import java.util.UUID;

public record CaptureItemResult(
        UUID id,
        UUID captureId,
        String originalText,
        CaptureCategory category,
        ProcessingStatus processingStatus,
        Instant createdAt
) {
}

package com.blaie.blaie_be.capture.api.response;

import com.blaie.blaie_be.capture.application.result.CaptureItemResult;
import java.time.Instant;
import java.util.UUID;

public record CaptureItemResponse(
        UUID id,
        UUID captureId,
        String originalText,
        String category,
        String processingStatus,
        Instant createdAt
) {
    public static CaptureItemResponse from(CaptureItemResult item) {
        return new CaptureItemResponse(
                item.id(),
                item.captureId(),
                item.originalText(),
                item.category() == null ? null : item.category().value(),
                item.processingStatus().value(),
                item.createdAt()
        );
    }
}

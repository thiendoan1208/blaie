package com.blaie.blaie_be.capture.api.response;

import com.blaie.blaie_be.capture.application.result.CaptureResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaptureResponse(
        UUID id,
        String originalText,
        String processingStatus,
        String failureCode,
        boolean canRetry,
        List<CaptureItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static CaptureResponse from(CaptureResult capture) {
        return new CaptureResponse(
                capture.id(),
                capture.originalText(),
                capture.processingStatus().value(),
                capture.failureCode(),
                capture.canRetry(),
                capture.items().stream().map(CaptureItemResponse::from).toList(),
                capture.createdAt(),
                capture.updatedAt()
        );
    }
}

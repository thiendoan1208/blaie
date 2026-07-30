package com.blaie.blaie_be.capture.api.response;

import com.blaie.blaie_be.capture.application.result.CaptureResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaptureResponse(
        UUID id,
        String inputType,
        String originalText,
        String processingStatus,
        String failureCode,
        boolean canRetry,
        List<CaptureAssetResponse> attachments,
        List<CaptureItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static CaptureResponse from(CaptureResult capture) {
        return new CaptureResponse(
                capture.id(),
                capture.inputType().value(),
                capture.originalText(),
                capture.processingStatus().value(),
                capture.failureCode(),
                capture.canRetry(),
                capture.assets().stream()
                        .map(asset -> CaptureAssetResponse.from(capture.id(), asset))
                        .toList(),
                capture.items().stream().map(CaptureItemResponse::from).toList(),
                capture.createdAt(),
                capture.updatedAt()
        );
    }
}

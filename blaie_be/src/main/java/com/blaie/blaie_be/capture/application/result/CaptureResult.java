package com.blaie.blaie_be.capture.application.result;

import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.capture.domain.CaptureInputType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaptureResult(
        UUID id,
        CaptureInputType inputType,
        String originalText,
        ProcessingStatus processingStatus,
        String failureCode,
        boolean canRetry,
        List<CaptureAssetResult> assets,
        List<CaptureItemResult> items,
        Instant createdAt,
        Instant updatedAt
) {
    public CaptureResult {
        assets = List.copyOf(assets);
        items = List.copyOf(items);
    }

    public CaptureResult(
            UUID id,
            String originalText,
            ProcessingStatus processingStatus,
            String failureCode,
            boolean canRetry,
            List<CaptureItemResult> items,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                CaptureInputType.TEXT,
                originalText,
                processingStatus,
                failureCode,
                canRetry,
                List.of(),
                items,
                createdAt,
                updatedAt
        );
    }

    public CaptureResult(
            UUID id,
            String originalText,
            ProcessingStatus processingStatus,
            List<CaptureItemResult> items,
            Instant createdAt
    ) {
        this(
                id,
                CaptureInputType.TEXT,
                originalText,
                processingStatus,
                null,
                false,
                List.of(),
                items,
                createdAt,
                createdAt
        );
    }
}

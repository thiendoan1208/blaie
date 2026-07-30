package com.blaie.blaie_be.capture.application.result;

import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.CaptureInputType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProcessingJobResult(
        UUID id,
        UUID captureId,
        String originRequestId,
        String jobType,
        CaptureInputType inputType,
        String originalText,
        List<ProcessingAssetResult> assets,
        ProcessingJobStatus status,
        int attemptCount,
        int maxAttempts,
        int retryGeneration,
        int dispatchGeneration,
        Instant availableAt
) {
    public ProcessingJobResult {
        assets = List.copyOf(assets);
    }

    public ProcessingJobResult(
            UUID id,
            UUID captureId,
            String originRequestId,
            String originalText,
            ProcessingJobStatus status,
            int attemptCount,
            int maxAttempts,
            int retryGeneration,
            int dispatchGeneration,
            Instant availableAt
    ) {
        this(
                id,
                captureId,
                originRequestId,
                "text_classification",
                CaptureInputType.TEXT,
                originalText,
                List.of(),
                status,
                attemptCount,
                maxAttempts,
                retryGeneration,
                dispatchGeneration,
                availableAt
        );
    }
}

package com.blaie.blaie_be.capture.application.result;

import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import java.time.Instant;
import java.util.UUID;

public record ProcessingJobResult(
        UUID id,
        UUID captureId,
        String originalText,
        ProcessingJobStatus status,
        int attemptCount,
        int maxAttempts,
        int retryGeneration,
        int dispatchGeneration,
        Instant availableAt
) {
}

package com.blaie.blaie_be.capture.application.result;

import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.time.Instant;
import java.util.UUID;

public record AdminProcessingJobResult(
        UUID id,
        UUID captureId,
        UUID userId,
        String jobType,
        ProcessingJobStatus status,
        int attemptCount,
        int maxAttempts,
        int retryGeneration,
        int dispatchGeneration,
        String correlationId,
        Instant availableAt,
        Instant leaseExpiresAt,
        String lastErrorCode,
        TextClassificationFailureClass lastFailureClass,
        boolean manualRetryAllowed,
        Instant lastDispatchedAt,
        Instant nextDispatchAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}

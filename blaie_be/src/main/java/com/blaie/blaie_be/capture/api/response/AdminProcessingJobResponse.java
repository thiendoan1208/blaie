package com.blaie.blaie_be.capture.api.response;

import com.blaie.blaie_be.capture.application.result.AdminProcessingJobResult;
import java.time.Instant;
import java.util.UUID;

public record AdminProcessingJobResponse(
        UUID id,
        UUID captureId,
        UUID userId,
        String jobType,
        String status,
        int attemptCount,
        int maxAttempts,
        int retryGeneration,
        int dispatchGeneration,
        String correlationId,
        Instant availableAt,
        Instant leaseExpiresAt,
        String lastErrorCode,
        String lastFailureClass,
        boolean manualRetryAllowed,
        Instant lastDispatchedAt,
        Instant nextDispatchAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static AdminProcessingJobResponse from(AdminProcessingJobResult result) {
        return new AdminProcessingJobResponse(
                result.id(),
                result.captureId(),
                result.userId(),
                result.jobType(),
                result.status().value(),
                result.attemptCount(),
                result.maxAttempts(),
                result.retryGeneration(),
                result.dispatchGeneration(),
                result.correlationId(),
                result.availableAt(),
                result.leaseExpiresAt(),
                result.lastErrorCode(),
                result.lastFailureClass() == null ? null : result.lastFailureClass().value(),
                result.manualRetryAllowed(),
                result.lastDispatchedAt(),
                result.nextDispatchAt(),
                result.createdAt(),
                result.updatedAt(),
                result.completedAt()
        );
    }
}

package com.blaie.blaie_be.capture.infrastructure.persistence.admin;

import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobResult;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.infrastructure.persistence.ProcessingJobEntity;

final class AdminProcessingJobMapper {
    private AdminProcessingJobMapper() {
    }

    static AdminProcessingJobResult toResult(ProcessingJobEntity job) {
        return new AdminProcessingJobResult(
                job.id(),
                job.captureId(),
                job.userId(),
                job.jobType(),
                ProcessingJobStatus.fromValue(job.status()),
                job.attemptCount(),
                job.maxAttempts(),
                job.retryGeneration(),
                job.dispatchGeneration(),
                job.originRequestId(),
                job.availableAt(),
                job.leaseExpiresAt(),
                job.lastErrorCode(),
                job.lastFailureClass(),
                job.manualRetryAllowed(),
                job.lastDispatchedAt(),
                job.nextDispatchAt(),
                job.createdAt(),
                job.updatedAt(),
                job.completedAt()
        );
    }
}

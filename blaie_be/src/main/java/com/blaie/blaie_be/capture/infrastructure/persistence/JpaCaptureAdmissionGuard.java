package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.error.RateLimitedException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class JpaCaptureAdmissionGuard {
    private final ProcessingJobRepository jobRepository;
    private final CaptureProcessingSettingsPort settings;

    JpaCaptureAdmissionGuard(
            ProcessingJobRepository jobRepository,
            CaptureProcessingSettingsPort settings
    ) {
        this.jobRepository = jobRepository;
        this.settings = settings;
    }

    void acquireGlobalMutex() {
        if (jobRepository.acquireCaptureAdmissionMutex() != 1) {
            throw new IllegalStateException("Capture admission mutex is missing");
        }
    }

    void requireCapacity(UUID userId, Instant now) {
        int userLimit = settings.maxActiveJobsPerUser();
        if (jobRepository.countActiveForUserUpTo(userId, userLimit) >= userLimit) {
            throw new RateLimitedException(
                    ErrorCode.TOO_MANY_ACTIVE_JOBS,
                    ErrorCode.TOO_MANY_ACTIVE_JOBS.defaultMessage(),
                    settings.admissionRetryAfter()
            );
        }

        int totalLimit = settings.maxActiveJobsTotal();
        if (jobRepository.countActiveUpTo(totalLimit) >= totalLimit
                || jobRepository.existsQueuedOlderThan(now.minus(settings.maxOldestQueuedAge()))) {
            throw new RateLimitedException(
                    ErrorCode.CAPTURE_PROCESSING_OVERLOADED,
                    ErrorCode.CAPTURE_PROCESSING_OVERLOADED.defaultMessage(),
                    settings.admissionRetryAfter()
            );
        }
    }
}

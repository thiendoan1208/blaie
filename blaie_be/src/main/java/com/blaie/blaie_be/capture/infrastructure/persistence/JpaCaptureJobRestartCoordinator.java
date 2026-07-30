package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.port.CaptureJobDispatchPort;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaCaptureJobRestartCoordinator {
    private final CaptureItemRepository captureItemRepository;
    private final CaptureRepository captureRepository;
    private final ProcessingJobRepository jobRepository;
    private final JpaCaptureAdmissionGuard admissionGuard;
    private final CaptureProcessingSettingsPort settings;
    private final CaptureJobDispatchPort jobDispatch;

    public JpaCaptureJobRestartCoordinator(
            CaptureItemRepository captureItemRepository,
            CaptureRepository captureRepository,
            ProcessingJobRepository jobRepository,
            JpaCaptureAdmissionGuard admissionGuard,
            CaptureProcessingSettingsPort settings,
            CaptureJobDispatchPort jobDispatch
    ) {
        this.captureItemRepository = captureItemRepository;
        this.captureRepository = captureRepository;
        this.jobRepository = jobRepository;
        this.admissionGuard = admissionGuard;
        this.settings = settings;
        this.jobDispatch = jobDispatch;
    }

    public void restart(
            ProcessingJobEntity job,
            CaptureEntity capture,
            Instant now,
            ErrorCode rejectionCode
    ) {
        if (!ProcessingJobStatus.DEAD.value().equals(job.status())
                || !ProcessingStatus.FAILED.value().equals(capture.processingStatus())
                || !job.manualRetryAllowed()) {
            throw new AppException(rejectionCode);
        }

        admissionGuard.acquireGlobalMutex();
        admissionGuard.requireCapacity(job.userId(), now);
        captureItemRepository.deleteByCaptureId(capture.id());
        capture.restart();
        job.restart(now, now.plus(settings.dispatchRetryDelay(job.dispatchGeneration() + 1)));
        captureRepository.flush();
        jobRepository.flush();
        jobDispatch.publish(
                job.id(),
                job.captureId(),
                job.dispatchGeneration(),
                job.originRequestId()
        );
    }
}

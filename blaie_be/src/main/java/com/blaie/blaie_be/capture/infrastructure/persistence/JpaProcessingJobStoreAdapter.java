package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult.RecoveryOutcome;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaProcessingJobStoreAdapter implements ProcessingJobStorePort {
    private static final String STALE_JOB_ERROR = "job_lease_expired";

    private final ProcessingJobRepository jobRepository;
    private final CaptureRepository captureRepository;
    private final CaptureItemRepository captureItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CaptureProcessingSettingsPort settings;

    public JpaProcessingJobStoreAdapter(
            ProcessingJobRepository jobRepository,
            CaptureRepository captureRepository,
            CaptureItemRepository captureItemRepository,
            ApplicationEventPublisher eventPublisher,
            CaptureProcessingSettingsPort settings
    ) {
        this.jobRepository = jobRepository;
        this.captureRepository = captureRepository;
        this.captureItemRepository = captureItemRepository;
        this.eventPublisher = eventPublisher;
        this.settings = settings;
    }

    @Override
    @Transactional
    public boolean extendLease(
            UUID jobId,
            String workerId,
            int attemptCount,
            int retryGeneration,
            Instant leaseExpiresAt
    ) {
        ProcessingJobEntity job = jobRepository.findLockedById(jobId).orElse(null);
        return job != null && job.extendLease(
                workerId,
                attemptCount,
                retryGeneration,
                leaseExpiresAt
        );
    }

    @Override
    @Transactional
    public Optional<ProcessingJobResult> claim(
            UUID jobId,
            int dispatchGeneration,
            String workerId,
            Instant now,
            Instant leaseExpiresAt
    ) {
        ProcessingJobEntity job = jobRepository.findLockedById(jobId).orElse(null);
        if (job == null) {
            return Optional.empty();
        }
        CaptureEntity capture = captureRepository.findById(job.captureId())
                .orElseThrow(() -> new IllegalStateException("Capture for processing job is missing"));
        if (!ProcessingStatus.PROCESSING.value().equals(capture.processingStatus())
                || !job.claim(dispatchGeneration, workerId, now, leaseExpiresAt)) {
            return Optional.empty();
        }
        return Optional.of(toResult(job, capture.originalText()));
    }

    @Override
    @Transactional
    public boolean complete(
            UUID jobId,
            String workerId,
            int attemptCount,
            int retryGeneration,
            CaptureAnalysis analysis,
            Instant now
    ) {
        ProcessingJobEntity job = jobRepository.findLockedById(jobId).orElse(null);
        if (job == null || !job.ownsLease(workerId, attemptCount, retryGeneration)) {
            return false;
        }
        CaptureEntity capture = captureRepository.findLockedById(job.captureId())
                .orElseThrow(() -> new IllegalStateException("Capture for processing job is missing"));
        if (ProcessingStatus.COMPLETED.value().equals(capture.processingStatus())) {
            job.complete(now);
            return true;
        }
        if (!ProcessingStatus.PROCESSING.value().equals(capture.processingStatus())) {
            return false;
        }

        captureItemRepository.deleteByCaptureId(capture.id());
        List<CaptureItemEntity> items = IntStream.range(0, analysis.items().size())
                .mapToObj(position -> CaptureItemEntity.completed(
                        capture,
                        analysis.items().get(position),
                        position
                ))
                .toList();
        captureItemRepository.saveAll(items);
        capture.complete(analysis);
        job.complete(now);
        return true;
    }

    @Override
    @Transactional
    public boolean scheduleRetry(
            UUID jobId,
            String workerId,
            int attemptCount,
            int retryGeneration,
            String errorCode,
            TextClassificationFailureClass failureClass,
            Instant availableAt,
            Instant now
    ) {
        ProcessingJobEntity job = jobRepository.findLockedById(jobId).orElse(null);
        if (job == null || !job.ownsLease(workerId, attemptCount, retryGeneration)) {
            return false;
        }
        job.scheduleRetry(errorCode, failureClass, availableAt);
        return true;
    }

    @Override
    @Transactional
    public boolean markDead(
            UUID jobId,
            String workerId,
            int attemptCount,
            int retryGeneration,
            String errorCode,
            TextClassificationFailureClass failureClass,
            Instant now
    ) {
        ProcessingJobEntity job = jobRepository.findLockedById(jobId).orElse(null);
        if (job == null || !job.ownsLease(workerId, attemptCount, retryGeneration)) {
            return false;
        }
        CaptureEntity capture = captureRepository.findLockedById(job.captureId())
                .orElseThrow(() -> new IllegalStateException("Capture for processing job is missing"));
        job.dead(errorCode, failureClass, now);
        if (!ProcessingStatus.COMPLETED.value().equals(capture.processingStatus())) {
            capture.fail(errorCode);
            captureItemRepository.deleteByCaptureId(capture.id());
        }
        return true;
    }

    @Override
    @Transactional
    public List<RecoveredJobResult> recoverStale(Instant now) {
        List<ProcessingJobEntity> staleJobs = jobRepository.findStale(now, PageRequest.of(0, 100));
        List<RecoveredJobResult> recovered = new ArrayList<>(staleJobs.size());
        for (ProcessingJobEntity job : staleJobs) {
            CaptureEntity capture = captureRepository.findLockedById(job.captureId())
                    .orElseThrow(() -> new IllegalStateException("Capture for processing job is missing"));
            if (job.attemptCount() < job.maxAttempts()) {
                job.scheduleRetry(
                        STALE_JOB_ERROR,
                        TextClassificationFailureClass.SYSTEM_RETRYABLE,
                        now.plus(settings.retryDelay(job.attemptCount()))
                );
                recovered.add(new RecoveredJobResult(
                        job.id(),
                        job.captureId(),
                        RecoveryOutcome.RETRY_SCHEDULED,
                        TextClassificationFailureClass.SYSTEM_RETRYABLE
                ));
            } else {
                job.dead(
                        STALE_JOB_ERROR,
                        TextClassificationFailureClass.SYSTEM_RETRYABLE,
                        now
                );
                if (!ProcessingStatus.COMPLETED.value().equals(capture.processingStatus())) {
                    capture.fail(STALE_JOB_ERROR);
                    captureItemRepository.deleteByCaptureId(capture.id());
                }
                recovered.add(new RecoveredJobResult(
                        job.id(),
                        job.captureId(),
                        RecoveryOutcome.DEAD,
                        TextClassificationFailureClass.SYSTEM_RETRYABLE
                ));
            }
        }
        return List.copyOf(recovered);
    }

    @Override
    @Transactional
    public int dispatchReadyRetries(Instant now, int limit) {
        List<ProcessingJobEntity> jobs = jobRepository.findReadyRetries(now, PageRequest.of(0, limit));
        for (ProcessingJobEntity job : jobs) {
            dispatch(job, now);
        }
        return jobs.size();
    }

    @Override
    @Transactional
    public int redispatchStaleQueued(Instant now, int limit) {
        List<ProcessingJobEntity> jobs = jobRepository.findDueDispatches(now, PageRequest.of(0, limit));
        for (ProcessingJobEntity job : jobs) {
            dispatch(job, now);
        }
        return jobs.size();
    }

    private void dispatch(ProcessingJobEntity job, Instant now) {
        int nextGeneration = job.dispatchGeneration() + 1;
        job.dispatch(now, now.plus(settings.dispatchRetryDelay(nextGeneration)));
        eventPublisher.publishEvent(new TextCaptureQueuedEvent(
                UUID.randomUUID(),
                job.id(),
                job.captureId(),
                job.dispatchGeneration(),
                job.originRequestId()
        ));
    }

    private ProcessingJobResult toResult(ProcessingJobEntity job, String originalText) {
        return new ProcessingJobResult(
                job.id(),
                job.captureId(),
                job.originRequestId(),
                originalText,
                ProcessingJobStatus.fromValue(job.status()),
                job.attemptCount(),
                job.maxAttempts(),
                job.retryGeneration(),
                job.dispatchGeneration(),
                job.availableAt()
        );
    }
}

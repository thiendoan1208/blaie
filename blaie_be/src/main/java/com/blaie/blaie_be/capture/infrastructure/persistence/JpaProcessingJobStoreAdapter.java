package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.result.ProcessingJobResult;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import java.time.Duration;
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

    public JpaProcessingJobStoreAdapter(
            ProcessingJobRepository jobRepository,
            CaptureRepository captureRepository,
            CaptureItemRepository captureItemRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.jobRepository = jobRepository;
        this.captureRepository = captureRepository;
        this.captureItemRepository = captureItemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Optional<ProcessingJobResult> claim(
            UUID jobId,
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
                || !job.claim(workerId, now, leaseExpiresAt)) {
            return Optional.empty();
        }
        return Optional.of(toResult(job, capture.originalText()));
    }

    @Override
    @Transactional
    public void complete(UUID jobId, CaptureAnalysis analysis, Instant now) {
        ProcessingJobEntity job = jobRepository.findLockedById(jobId).orElse(null);
        if (job == null || ProcessingJobStatus.COMPLETED.value().equals(job.status())) {
            return;
        }
        if (!ProcessingJobStatus.PROCESSING.value().equals(job.status())) {
            return;
        }
        CaptureEntity capture = captureRepository.findLockedById(job.captureId())
                .orElseThrow(() -> new IllegalStateException("Capture for processing job is missing"));
        if (ProcessingStatus.COMPLETED.value().equals(capture.processingStatus())) {
            job.complete(now);
            return;
        }
        if (!ProcessingStatus.PROCESSING.value().equals(capture.processingStatus())) {
            return;
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
    }

    @Override
    @Transactional
    public void scheduleRetry(UUID jobId, String errorCode, Instant availableAt, Instant now) {
        ProcessingJobEntity job = jobRepository.findLockedById(jobId).orElse(null);
        if (job == null || !ProcessingJobStatus.PROCESSING.value().equals(job.status())) {
            return;
        }
        job.scheduleRetry(errorCode, availableAt);
    }

    @Override
    @Transactional
    public void markDead(UUID jobId, String errorCode, Instant now) {
        ProcessingJobEntity job = jobRepository.findLockedById(jobId).orElse(null);
        if (job == null || ProcessingJobStatus.COMPLETED.value().equals(job.status())
                || ProcessingJobStatus.DEAD.value().equals(job.status())) {
            return;
        }
        CaptureEntity capture = captureRepository.findLockedById(job.captureId())
                .orElseThrow(() -> new IllegalStateException("Capture for processing job is missing"));
        job.dead(errorCode, now);
        if (!ProcessingStatus.COMPLETED.value().equals(capture.processingStatus())) {
            capture.fail(errorCode);
            captureItemRepository.deleteByCaptureId(capture.id());
        }
    }

    @Override
    @Transactional
    public List<RecoveredJobResult> recoverStale(Instant now, Duration retryDelay) {
        List<ProcessingJobEntity> staleJobs = jobRepository.findStale(now, PageRequest.of(0, 100));
        List<RecoveredJobResult> recovered = new ArrayList<>(staleJobs.size());
        for (ProcessingJobEntity job : staleJobs) {
            CaptureEntity capture = captureRepository.findLockedById(job.captureId())
                    .orElseThrow(() -> new IllegalStateException("Capture for processing job is missing"));
            if (job.attemptCount() < job.maxAttempts()) {
                job.scheduleRetry(STALE_JOB_ERROR, now.plus(retryDelay));
                recovered.add(new RecoveredJobResult(job.id(), job.captureId(), false));
            } else {
                job.dead(STALE_JOB_ERROR, now);
                if (!ProcessingStatus.COMPLETED.value().equals(capture.processingStatus())) {
                    capture.fail(STALE_JOB_ERROR);
                    captureItemRepository.deleteByCaptureId(capture.id());
                }
                recovered.add(new RecoveredJobResult(job.id(), job.captureId(), false));
            }
        }
        return List.copyOf(recovered);
    }

    @Override
    @Transactional
    public int dispatchReadyRetries(Instant now, int limit) {
        List<ProcessingJobEntity> jobs = jobRepository.findReadyRetries(now, PageRequest.of(0, limit));
        for (ProcessingJobEntity job : jobs) {
            job.dispatch();
            eventPublisher.publishEvent(
                    new TextCaptureQueuedEvent(UUID.randomUUID(), job.id(), job.captureId())
            );
        }
        return jobs.size();
    }

    private ProcessingJobResult toResult(ProcessingJobEntity job, String originalText) {
        return new ProcessingJobResult(
                job.id(),
                job.captureId(),
                originalText,
                ProcessingJobStatus.fromValue(job.status()),
                job.attemptCount(),
                job.maxAttempts(),
                job.retryGeneration(),
                job.availableAt()
        );
    }
}

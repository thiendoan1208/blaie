package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.CaptureAdminStorePort;
import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.result.AdminOutboxSummaryResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobCursor;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobMutationResult;
import com.blaie.blaie_be.capture.application.result.AdminProcessingJobResult;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaCaptureAdminStoreAdapter implements CaptureAdminStorePort {
    static final String OPERATOR_MARKED_DEAD = "operator_marked_dead";
    private static final String CAPTURE_PUBLISHER_LISTENER = "capture-text-job-redis-publisher";
    private static final String CAPTURE_EVENT_TYPE = TextCaptureQueuedEvent.class.getName();

    private final ProcessingJobRepository jobRepository;
    private final CaptureRepository captureRepository;
    private final CaptureItemRepository captureItemRepository;
    private final JpaCaptureAdmissionGuard admissionGuard;
    private final CaptureProcessingSettingsPort settings;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    public JpaCaptureAdminStoreAdapter(
            ProcessingJobRepository jobRepository,
            CaptureRepository captureRepository,
            CaptureItemRepository captureItemRepository,
            JpaCaptureAdmissionGuard admissionGuard,
            CaptureProcessingSettingsPort settings,
            ApplicationEventPublisher eventPublisher,
            JdbcTemplate jdbcTemplate
    ) {
        this.jobRepository = jobRepository;
        this.captureRepository = captureRepository;
        this.captureItemRepository = captureItemRepository;
        this.admissionGuard = admissionGuard;
        this.settings = settings;
        this.eventPublisher = eventPublisher;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminProcessingJobResult> findJobs(
            ProcessingJobStatus status,
            boolean stuck,
            AdminProcessingJobCursor cursor,
            Instant now,
            int limit
    ) {
        return jobRepository.findAdminPage(
                        status == null ? null : status.value(),
                        stuck,
                        now,
                        cursor == null ? null : cursor.createdAt(),
                        cursor == null ? null : cursor.jobId(),
                        PageRequest.of(0, limit)
                ).stream()
                .map(this::toResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminProcessingJobResult> findJob(UUID jobId) {
        return jobRepository.findById(jobId).map(this::toResult);
    }

    @Override
    @Transactional
    public AdminProcessingJobMutationResult requeue(UUID jobId, Instant now) {
        ProcessingJobEntity job = lockedJob(jobId);
        CaptureEntity capture = lockedCapture(job.captureId());
        ProcessingJobStatus previousStatus = ProcessingJobStatus.fromValue(job.status());
        if (previousStatus != ProcessingJobStatus.DEAD
                || !ProcessingStatus.FAILED.value().equals(capture.processingStatus())
                || !job.manualRetryAllowed()) {
            throw new AppException(ErrorCode.PROCESSING_JOB_REQUEUE_NOT_ALLOWED);
        }

        admissionGuard.acquireGlobalMutex();
        admissionGuard.requireCapacity(job.userId(), now);

        captureItemRepository.deleteByCaptureId(capture.id());
        capture.restart();
        job.restart(now, now.plus(settings.dispatchRetryDelay(job.dispatchGeneration() + 1)));
        captureRepository.flush();
        jobRepository.flush();
        publishDispatch(job);
        return new AdminProcessingJobMutationResult(previousStatus, toResult(job));
    }

    @Override
    @Transactional
    public AdminProcessingJobMutationResult markDead(UUID jobId, Instant now) {
        ProcessingJobEntity job = lockedJob(jobId);
        CaptureEntity capture = lockedCapture(job.captureId());
        ProcessingJobStatus previousStatus = ProcessingJobStatus.fromValue(job.status());
        if (!isActive(previousStatus)
                || !ProcessingStatus.PROCESSING.value().equals(capture.processingStatus())) {
            throw new AppException(ErrorCode.PROCESSING_JOB_MARK_DEAD_NOT_ALLOWED);
        }

        job.dead(
                OPERATOR_MARKED_DEAD,
                TextClassificationFailureClass.SYSTEM_RETRYABLE,
                now
        );
        capture.fail(OPERATOR_MARKED_DEAD);
        captureItemRepository.deleteByCaptureId(capture.id());
        captureRepository.flush();
        jobRepository.flush();
        return new AdminProcessingJobMutationResult(previousStatus, toResult(job));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOutboxSummaryResult outboxSummary(Instant now) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS backlog_count,
                       MIN(publication_date) AS oldest_publication_at,
                       MAX(last_resubmission_date) AS last_resubmission_at,
                       COALESCE(MAX(completion_attempts), 0) AS max_completion_attempts
                  FROM event_publication
                 WHERE event_type = ?
                   AND listener_id = ?
                   AND completion_date IS NULL
                """,
                (resultSet, rowNumber) -> {
                    Instant oldest = instant(resultSet.getObject(
                            "oldest_publication_at",
                            OffsetDateTime.class
                    ));
                    Instant lastResubmission = instant(resultSet.getObject(
                            "last_resubmission_at",
                            OffsetDateTime.class
                    ));
                    long oldestAgeSeconds = oldest == null
                            ? 0
                            : Math.max(0, Duration.between(oldest, now).toSeconds());
                    return new AdminOutboxSummaryResult(
                            resultSet.getLong("backlog_count"),
                            oldest,
                            oldestAgeSeconds,
                            lastResubmission,
                            resultSet.getInt("max_completion_attempts")
                    );
                },
                CAPTURE_EVENT_TYPE,
                CAPTURE_PUBLISHER_LISTENER
        );
    }

    private ProcessingJobEntity lockedJob(UUID jobId) {
        return jobRepository.findLockedById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.PROCESSING_JOB_NOT_FOUND));
    }

    private CaptureEntity lockedCapture(UUID captureId) {
        return captureRepository.findLockedById(captureId)
                .orElseThrow(() -> new IllegalStateException("Capture for processing job is missing"));
    }

    private boolean isActive(ProcessingJobStatus status) {
        return status == ProcessingJobStatus.QUEUED
                || status == ProcessingJobStatus.PROCESSING
                || status == ProcessingJobStatus.RETRY_WAIT;
    }

    private void publishDispatch(ProcessingJobEntity job) {
        eventPublisher.publishEvent(new TextCaptureQueuedEvent(
                UUID.randomUUID(),
                job.id(),
                job.captureId(),
                job.dispatchGeneration(),
                job.originRequestId()
        ));
    }

    private AdminProcessingJobResult toResult(ProcessingJobEntity job) {
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

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}

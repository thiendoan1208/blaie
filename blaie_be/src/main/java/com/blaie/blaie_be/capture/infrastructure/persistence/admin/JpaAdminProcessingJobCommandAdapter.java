package com.blaie.blaie_be.capture.infrastructure.persistence.admin;

import com.blaie.blaie_be.capture.application.admin.port.AdminProcessingJobCommandPort;
import com.blaie.blaie_be.capture.application.admin.result.AdminProcessingJobMutationResult;
import com.blaie.blaie_be.capture.domain.ProcessingJobStatus;
import com.blaie.blaie_be.capture.domain.ProcessingStatus;
import com.blaie.blaie_be.capture.domain.CaptureFailureClass;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureEntity;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureItemRepository;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureRepository;
import com.blaie.blaie_be.capture.infrastructure.persistence.JpaCaptureJobRestartCoordinator;
import com.blaie.blaie_be.capture.infrastructure.persistence.ProcessingJobEntity;
import com.blaie.blaie_be.capture.infrastructure.persistence.ProcessingJobRepository;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAdminProcessingJobCommandAdapter implements AdminProcessingJobCommandPort {
    static final String OPERATOR_MARKED_DEAD = "operator_marked_dead";

    private final ProcessingJobRepository jobRepository;
    private final CaptureRepository captureRepository;
    private final CaptureItemRepository captureItemRepository;
    private final JpaCaptureJobRestartCoordinator restartCoordinator;
    private final JdbcTemplate jdbcTemplate;

    public JpaAdminProcessingJobCommandAdapter(
            ProcessingJobRepository jobRepository,
            CaptureRepository captureRepository,
            CaptureItemRepository captureItemRepository,
            JpaCaptureJobRestartCoordinator restartCoordinator,
            JdbcTemplate jdbcTemplate
    ) {
        this.jobRepository = jobRepository;
        this.captureRepository = captureRepository;
        this.captureItemRepository = captureItemRepository;
        this.restartCoordinator = restartCoordinator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public AdminProcessingJobMutationResult requeue(
            UUID jobId,
            UUID actorId,
            String requestId,
            Instant now
    ) {
        ProcessingJobEntity job = lockedJob(jobId);
        CaptureEntity capture = lockedCapture(job.captureId());
        ProcessingJobStatus previousStatus = ProcessingJobStatus.fromValue(job.status());
        restartCoordinator.restart(job, capture, now, ErrorCode.PROCESSING_JOB_REQUEUE_NOT_ALLOWED);
        appendOperation(job, actorId, requestId, "requeue", null, previousStatus, now);
        return new AdminProcessingJobMutationResult(
                previousStatus,
                AdminProcessingJobMapper.toResult(job)
        );
    }

    @Override
    @Transactional
    public AdminProcessingJobMutationResult markDead(
            UUID jobId,
            String reason,
            UUID actorId,
            String requestId,
            Instant now
    ) {
        ProcessingJobEntity job = lockedJob(jobId);
        CaptureEntity capture = lockedCapture(job.captureId());
        ProcessingJobStatus previousStatus = ProcessingJobStatus.fromValue(job.status());
        if (!isActive(previousStatus)
                || !ProcessingStatus.PROCESSING.value().equals(capture.processingStatus())) {
            throw new AppException(ErrorCode.PROCESSING_JOB_MARK_DEAD_NOT_ALLOWED);
        }

        job.dead(OPERATOR_MARKED_DEAD, CaptureFailureClass.SYSTEM_RETRYABLE, now);
        capture.fail(OPERATOR_MARKED_DEAD);
        captureItemRepository.deleteByCaptureId(capture.id());
        captureRepository.flush();
        jobRepository.flush();
        appendOperation(job, actorId, requestId, "mark_dead", reason, previousStatus, now);
        return new AdminProcessingJobMutationResult(
                previousStatus,
                AdminProcessingJobMapper.toResult(job)
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

    private void appendOperation(
            ProcessingJobEntity job,
            UUID actorId,
            String requestId,
            String operation,
            String reason,
            ProcessingJobStatus previousStatus,
            Instant now
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO capture_admin_job_operations (
                    id, job_id, capture_id, actor_id, request_id, operation,
                    reason, previous_status, new_status, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                job.id(),
                job.captureId(),
                actorId.toString(),
                requestId,
                operation,
                reason,
                previousStatus.value(),
                job.status(),
                Timestamp.from(now)
        );
    }
}

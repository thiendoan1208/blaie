package com.blaie.blaie_be.capture.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "processing_jobs")
@EntityListeners(AuditingEntityListener.class)
public class ProcessingJobEntity {
    @Id
    private UUID id;

    @Column(name = "capture_id", nullable = false)
    private UUID captureId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_type", nullable = false, length = 50)
    private String jobType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "retry_generation", nullable = false)
    private int retryGeneration;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ProcessingJobEntity() {
    }

    public static ProcessingJobEntity queued(CaptureEntity capture, int maxAttempts, Instant now) {
        ProcessingJobEntity job = new ProcessingJobEntity();
        job.id = UUID.randomUUID();
        job.captureId = capture.id();
        job.userId = capture.userId();
        job.jobType = "text_classification";
        job.status = "queued";
        job.attemptCount = 0;
        job.maxAttempts = maxAttempts;
        job.retryGeneration = 0;
        job.availableAt = now;
        return job;
    }

    public boolean claim(String workerId, Instant now, Instant leaseUntil) {
        if (!"queued".equals(status) || availableAt.isAfter(now) || attemptCount >= maxAttempts) {
            return false;
        }
        status = "processing";
        attemptCount++;
        leaseOwner = workerId;
        leaseExpiresAt = leaseUntil;
        return true;
    }

    public boolean extendLease(String workerId, Instant leaseUntil) {
        if (!"processing".equals(status) || !Objects.equals(leaseOwner, workerId)) {
            return false;
        }
        leaseExpiresAt = leaseUntil;
        return true;
    }

    public void complete(Instant now) {
        status = "completed";
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = null;
        completedAt = now;
    }

    public void scheduleRetry(String errorCode, Instant retryAt) {
        status = "retry_wait";
        availableAt = retryAt;
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = errorCode;
        completedAt = null;
    }

    public void dispatch() {
        status = "queued";
    }

    public void dead(String errorCode, Instant now) {
        status = "dead";
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = errorCode;
        completedAt = now;
    }

    public void restart(Instant now) {
        status = "queued";
        attemptCount = 0;
        retryGeneration++;
        availableAt = now;
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = null;
        completedAt = null;
    }

    public UUID id() {
        return id;
    }

    public UUID captureId() {
        return captureId;
    }

    public String jobType() {
        return jobType;
    }

    public String status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public int retryGeneration() {
        return retryGeneration;
    }

    public Instant availableAt() {
        return availableAt;
    }

    public Instant leaseExpiresAt() {
        return leaseExpiresAt;
    }
}

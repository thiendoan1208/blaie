package com.blaie.blaie_be.capture.infrastructure.persistence;

import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import com.blaie.blaie_be.core.request.RequestIdPolicy;
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

    @Column(name = "origin_request_id", nullable = false, length = 128)
    private String originRequestId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "retry_generation", nullable = false)
    private int retryGeneration;

    @Column(name = "dispatch_generation", nullable = false)
    private int dispatchGeneration;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_failure_class", length = 32)
    private String lastFailureClass;

    @Column(name = "last_dispatched_at")
    private Instant lastDispatchedAt;

    @Column(name = "next_dispatch_at")
    private Instant nextDispatchAt;

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

    public static ProcessingJobEntity queued(
            CaptureEntity capture,
            int maxAttempts,
            String originRequestId,
            Instant now,
            Instant nextDispatchAt
    ) {
        ProcessingJobEntity job = new ProcessingJobEntity();
        job.id = UUID.randomUUID();
        job.captureId = capture.id();
        job.userId = capture.userId();
        job.jobType = "text_classification";
        if (!RequestIdPolicy.isValid(originRequestId)) {
            throw new IllegalArgumentException("originRequestId is invalid");
        }
        job.originRequestId = originRequestId;
        job.status = "queued";
        job.attemptCount = 0;
        job.maxAttempts = maxAttempts;
        job.retryGeneration = 0;
        job.availableAt = now;
        job.recordDispatch(now, nextDispatchAt);
        return job;
    }

    public boolean claim(
            int expectedDispatchGeneration,
            String workerId,
            Instant now,
            Instant leaseUntil
    ) {
        if (!"queued".equals(status)
                || dispatchGeneration != expectedDispatchGeneration
                || availableAt.isAfter(now)
                || attemptCount >= maxAttempts) {
            return false;
        }
        status = "processing";
        attemptCount++;
        leaseOwner = workerId;
        leaseExpiresAt = leaseUntil;
        nextDispatchAt = null;
        return true;
    }

    public boolean extendLease(
            String workerId,
            int expectedAttemptCount,
            int expectedRetryGeneration,
            Instant leaseUntil
    ) {
        if (!ownsLease(workerId, expectedAttemptCount, expectedRetryGeneration)) {
            return false;
        }
        leaseExpiresAt = leaseUntil;
        return true;
    }

    public boolean ownsLease(
            String workerId,
            int expectedAttemptCount,
            int expectedRetryGeneration
    ) {
        return "processing".equals(status)
                && Objects.equals(leaseOwner, workerId)
                && attemptCount == expectedAttemptCount
                && retryGeneration == expectedRetryGeneration;
    }

    public void complete(Instant now) {
        status = "completed";
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = null;
        lastFailureClass = null;
        completedAt = now;
        nextDispatchAt = null;
    }

    public void scheduleRetry(
            String errorCode,
            TextClassificationFailureClass failureClass,
            Instant retryAt
    ) {
        status = "retry_wait";
        availableAt = retryAt;
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = errorCode;
        lastFailureClass = Objects.requireNonNull(failureClass, "failureClass").value();
        completedAt = null;
        nextDispatchAt = null;
    }

    public void dispatch(Instant now, Instant nextDispatchAt) {
        status = "queued";
        recordDispatch(now, nextDispatchAt);
    }

    public void dead(
            String errorCode,
            TextClassificationFailureClass failureClass,
            Instant now
    ) {
        status = "dead";
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = errorCode;
        lastFailureClass = Objects.requireNonNull(failureClass, "failureClass").value();
        completedAt = now;
        nextDispatchAt = null;
    }

    public void restart(Instant now, Instant nextDispatchAt) {
        status = "queued";
        attemptCount = 0;
        retryGeneration++;
        availableAt = now;
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = null;
        lastFailureClass = null;
        completedAt = null;
        recordDispatch(now, nextDispatchAt);
    }

    private void recordDispatch(Instant now, Instant nextDispatchAt) {
        if (nextDispatchAt == null || nextDispatchAt.isBefore(now)) {
            throw new IllegalArgumentException("next dispatch time must not be before dispatch time");
        }
        dispatchGeneration++;
        lastDispatchedAt = now;
        this.nextDispatchAt = nextDispatchAt;
    }

    public UUID id() {
        return id;
    }

    public UUID captureId() {
        return captureId;
    }

    public UUID userId() {
        return userId;
    }

    public String jobType() {
        return jobType;
    }

    public String originRequestId() {
        return originRequestId;
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

    public int dispatchGeneration() {
        return dispatchGeneration;
    }

    public Instant availableAt() {
        return availableAt;
    }

    public Instant leaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String leaseOwner() {
        return leaseOwner;
    }

    public Instant lastDispatchedAt() {
        return lastDispatchedAt;
    }

    public Instant nextDispatchAt() {
        return nextDispatchAt;
    }

    public String lastErrorCode() {
        return lastErrorCode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public TextClassificationFailureClass lastFailureClass() {
        if (lastErrorCode == null) {
            return null;
        }
        TextClassificationFailureClass knownClass = knownFailureClass(lastErrorCode);
        if (knownClass != null) {
            return knownClass;
        }
        if (lastFailureClass != null) {
            return TextClassificationFailureClass.fromValue(lastFailureClass);
        }
        return TextClassificationFailureClass.SYSTEM_RETRYABLE;
    }

    public boolean manualRetryAllowed() {
        TextClassificationFailureClass failureClass = lastFailureClass();
        return "dead".equals(status)
                && failureClass != null
                && failureClass.manualRetryAllowed();
    }

    private TextClassificationFailureClass knownFailureClass(String errorCode) {
        return switch (errorCode) {
            case "sensitive_credential_detected", "content_policy_blocked" ->
                    TextClassificationFailureClass.CONTENT_TERMINAL;
            case "ai_not_configured", "ai_provider_not_configured", "ai_provider_rejected" ->
                    TextClassificationFailureClass.PROVIDER_TERMINAL;
            case "ai_provider_unavailable", "ai_invalid_response" ->
                    TextClassificationFailureClass.PROVIDER_RETRYABLE;
            case "job_lease_expired", "unexpected_classification_error" ->
                    TextClassificationFailureClass.SYSTEM_RETRYABLE;
            default -> null;
        };
    }
}

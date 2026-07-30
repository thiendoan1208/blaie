package com.blaie.blaie_be.capture.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_deletion_jobs")
public class StorageDeletionJobEntity {
    @Id
    private UUID id;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected StorageDeletionJobEntity() {
    }

    public static StorageDeletionJobEntity pending(String objectKey, int maxAttempts, Instant now) {
        StorageDeletionJobEntity job = new StorageDeletionJobEntity();
        job.id = UUID.randomUUID();
        job.objectKey = objectKey;
        job.status = "pending";
        job.maxAttempts = maxAttempts;
        job.availableAt = now;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public boolean claim(String workerId, Instant now, Instant leaseUntil) {
        if ((!"pending".equals(status) && !"retry_wait".equals(status))
                || availableAt.isAfter(now)
                || attemptCount >= maxAttempts) {
            return false;
        }
        status = "processing";
        attemptCount++;
        leaseOwner = workerId;
        leaseExpiresAt = leaseUntil;
        updatedAt = now;
        return true;
    }

    public boolean retry(
            String workerId,
            int claimedAttempt,
            String errorCode,
            Instant retryAt,
            Instant now
    ) {
        if (!ownsLease(workerId, claimedAttempt)) {
            return false;
        }
        transitionToRetry(errorCode, retryAt, now);
        return true;
    }

    private void transitionToRetry(String errorCode, Instant retryAt, Instant now) {
        status = "retry_wait";
        availableAt = retryAt;
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = errorCode;
        updatedAt = now;
    }

    public boolean complete(String workerId, int claimedAttempt, Instant now) {
        if (!ownsLease(workerId, claimedAttempt)) {
            return false;
        }
        status = "completed";
        leaseOwner = null;
        leaseExpiresAt = null;
        lastErrorCode = null;
        completedAt = now;
        updatedAt = now;
        return true;
    }

    public void recover(Instant now) {
        if ("processing".equals(status)) {
            transitionToRetry("storage_delete_lease_expired", now, now);
        }
    }

    private boolean ownsLease(String workerId, int claimedAttempt) {
        return "processing".equals(status)
                && workerId.equals(leaseOwner)
                && claimedAttempt == attemptCount;
    }

    public UUID id() {
        return id;
    }

    public String objectKey() {
        return objectKey;
    }

    public String status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant availableAt() {
        return availableAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}

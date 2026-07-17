package com.blaie.blaie_be.capture.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "capture_idempotency_keys")
@IdClass(CaptureIdempotencyKeyId.class)
public class CaptureIdempotencyKeyEntity {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "capture_id", nullable = false)
    private UUID captureId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected CaptureIdempotencyKeyEntity() {
    }

    public String requestHash() {
        return requestHash;
    }

    public UUID captureId() {
        return captureId;
    }
}

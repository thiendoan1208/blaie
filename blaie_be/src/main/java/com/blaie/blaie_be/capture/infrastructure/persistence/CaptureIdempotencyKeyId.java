package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CaptureIdempotencyKeyId implements Serializable {
    private UUID userId;
    private UUID idempotencyKey;

    public CaptureIdempotencyKeyId() {
    }

    public CaptureIdempotencyKeyId(UUID userId, UUID idempotencyKey) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CaptureIdempotencyKeyId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, idempotencyKey);
    }
}

package com.blaie.blaie_be.auth.application.port;

import java.time.Instant;
import java.util.UUID;

public record AuthActionTokenView(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant revokedAt,
        int failedAttemptCount,
        Instant createdAt
) {
    public boolean isOpen(Instant now) {
        return consumedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}

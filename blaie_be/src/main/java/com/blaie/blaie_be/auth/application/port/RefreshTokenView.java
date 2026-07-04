package com.blaie.blaie_be.auth.application.port;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenView(
        AuthUserView user,
        UUID tokenFamilyId,
        Instant expiresAt,
        boolean revoked
) {
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}

package com.blaie.blaie_be.auth.application.result;

import java.time.Instant;
import java.util.UUID;

public record AuthUserResult(
        UUID id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        Instant createdAt
) {
}

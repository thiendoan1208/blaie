package com.blaie.blaie_be.auth.application.port;

import java.time.Instant;
import java.util.UUID;

public record AuthUserView(
        UUID id,
        String username,
        String email,
        String status,
        boolean admin,
        String displayName,
        String avatarUrl,
        Instant createdAt
) {
}

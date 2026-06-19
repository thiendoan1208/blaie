package com.blaie.blaie_be.auth.api.response;

import java.time.Instant;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        Instant createdAt
) {
}

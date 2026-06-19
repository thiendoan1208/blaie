package com.blaie.blaie_be.auth.infrastructure.security;

import java.time.Instant;
import java.util.UUID;

public record AccessTokenClaims(
        UUID userId,
        UUID tokenId,
        Instant expiresAt
) {
}

package com.blaie.blaie_be.auth.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenStorePort {
    void createWebRefreshToken(UUID userId, String tokenHash, UUID tokenFamilyId, Instant expiresAt, String userAgent);

    Optional<RefreshTokenView> findByTokenHash(String tokenHash);

    void rotateWebRefreshToken(String currentTokenHash, String nextTokenHash, Instant expiresAt, String userAgent, Instant now);

    void revokeRefreshToken(String tokenHash, String reason, Instant now);

    void revokeFamily(UUID tokenFamilyId, String reason, Instant now);

    void revokeAllUserTokens(UUID userId, String reason, Instant now);
}

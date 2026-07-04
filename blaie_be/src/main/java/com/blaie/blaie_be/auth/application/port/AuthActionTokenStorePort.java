package com.blaie.blaie_be.auth.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthActionTokenStorePort {
    void createToken(UUID userId, String type, String tokenHash, Instant expiresAt);

    Optional<AuthActionTokenView> findByTokenHashAndType(String tokenHash, String type);

    Optional<AuthActionTokenView> findLatestPendingForUpdate(UUID userId, String type);

    void consumeToken(UUID tokenId, Instant now);

    void revokeToken(UUID tokenId, String reason, Instant now);

    void incrementFailedAttempt(UUID tokenId);

    void revokeOpenTokens(UUID userId, String type, String reason, Instant now);

    boolean existsByTokenHash(String tokenHash);

    long countByUserIdAndTypeSince(UUID userId, String type, Instant since);

    Optional<Instant> findFirstCreatedAtByUserIdAndTypeSince(UUID userId, String type, Instant since);

    Optional<Instant> findLatestCreatedAtByUserIdAndType(UUID userId, String type);
}

package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptureIdempotencyKeyRepository
        extends JpaRepository<CaptureIdempotencyKeyEntity, CaptureIdempotencyKeyId> {

    Optional<CaptureIdempotencyKeyEntity> findByUserIdAndIdempotencyKey(UUID userId, UUID idempotencyKey);

    @Modifying
    @Query(value = """
            DELETE FROM capture_idempotency_keys
            WHERE user_id = :userId
              AND idempotency_key = :idempotencyKey
              AND expires_at <= :now
            """, nativeQuery = true)
    int deleteExpired(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") UUID idempotencyKey,
            @Param("now") Instant now
    );

    @Modifying
    @Query(value = """
            INSERT INTO capture_idempotency_keys (
                user_id, idempotency_key, request_hash, capture_id, created_at, expires_at
            ) VALUES (
                :userId, :idempotencyKey, :requestHash, :captureId, :createdAt, :expiresAt
            )
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") UUID idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("captureId") UUID captureId,
            @Param("createdAt") Instant createdAt,
            @Param("expiresAt") Instant expiresAt
    );
}

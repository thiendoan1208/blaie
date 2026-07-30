package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface StorageDeletionJobRepository extends JpaRepository<StorageDeletionJobEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO storage_deletion_jobs (
                id,
                object_key,
                status,
                attempt_count,
                max_attempts,
                available_at,
                created_at,
                updated_at
            )
            VALUES (:id, :objectKey, 'pending', 0, :maxAttempts, :now, :now, :now)
            ON CONFLICT (object_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("objectKey") String objectKey,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") Instant now
    );

    @Query(value = """
            SELECT * FROM storage_deletion_jobs
            WHERE status IN ('pending', 'retry_wait')
              AND available_at <= :now
              AND attempt_count < max_attempts
            ORDER BY available_at, created_at, id
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<StorageDeletionJobEntity> findReady(@Param("now") Instant now, Pageable pageable);

    @Query(value = """
            SELECT * FROM storage_deletion_jobs
            WHERE status = 'processing'
              AND lease_expires_at <= :now
            ORDER BY lease_expires_at, id
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<StorageDeletionJobEntity> findStale(@Param("now") Instant now, Pageable pageable);
}

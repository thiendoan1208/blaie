package com.blaie.blaie_be.capture.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJobEntity, UUID> {
    Optional<ProcessingJobEntity> findByCaptureIdAndJobType(UUID captureId, String jobType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProcessingJobEntity> findLockedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProcessingJobEntity> findLockedByCaptureIdAndJobType(UUID captureId, String jobType);

    @Query(value = """
            SELECT CAST(id AS INTEGER)
            FROM capture_admission_mutex
            WHERE id = 1
            FOR UPDATE
            """, nativeQuery = true)
    int acquireCaptureAdmissionMutex();

    @Query(value = """
            SELECT COUNT(*)
            FROM (
                SELECT 1
                FROM processing_jobs
                WHERE user_id = :userId
                  AND status IN ('queued', 'processing', 'retry_wait')
                LIMIT :limit
            ) active_jobs
            """, nativeQuery = true)
    long countActiveForUserUpTo(
            @Param("userId") UUID userId,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM (
                SELECT 1
                FROM processing_jobs
                WHERE status IN ('queued', 'processing', 'retry_wait')
                LIMIT :limit
            ) active_jobs
            """, nativeQuery = true)
    long countActiveUpTo(@Param("limit") int limit);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM processing_jobs
                WHERE status = 'queued'
                  AND available_at < :cutoff
            )
            """, nativeQuery = true)
    boolean existsQueuedOlderThan(@Param("cutoff") Instant cutoff);

    @Query(value = """
            SELECT * FROM processing_jobs
            WHERE status = 'processing'
              AND lease_expires_at <= :now
            ORDER BY lease_expires_at, id
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ProcessingJobEntity> findStale(@Param("now") Instant now, Pageable pageable);

    @Query(value = """
            SELECT * FROM processing_jobs
            WHERE status = 'retry_wait'
              AND available_at <= :now
            ORDER BY available_at, id
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ProcessingJobEntity> findReadyRetries(@Param("now") Instant now, Pageable pageable);

    @Query(value = """
            SELECT * FROM processing_jobs
            WHERE status = 'queued'
              AND (next_dispatch_at IS NULL OR next_dispatch_at <= :now)
            ORDER BY next_dispatch_at NULLS FIRST, id
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ProcessingJobEntity> findDueDispatches(@Param("now") Instant now, Pageable pageable);
}

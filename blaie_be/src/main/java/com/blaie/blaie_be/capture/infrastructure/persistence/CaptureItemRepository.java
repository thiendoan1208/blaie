package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptureItemRepository extends JpaRepository<CaptureItemEntity, UUID> {
    Optional<CaptureItemEntity> findByIdAndUserId(UUID id, UUID userId);

    List<CaptureItemEntity> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    List<CaptureItemEntity> findByCaptureIdOrderByItemPositionAsc(UUID captureId);

    void deleteByCaptureId(UUID captureId);

    @Query(value = """
            SELECT * FROM capture_items
            WHERE user_id = :userId
              AND (created_at < :createdAt OR (created_at = :createdAt AND id < :itemId))
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<CaptureItemEntity> findPageAfter(
            @Param("userId") UUID userId,
            @Param("createdAt") Instant createdAt,
            @Param("itemId") UUID itemId,
            Pageable pageable
    );
}

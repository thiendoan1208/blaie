package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface CaptureRepository extends JpaRepository<CaptureEntity, UUID> {
    Optional<CaptureEntity> findByIdAndUserId(UUID id, UUID userId);

    List<CaptureEntity> findByUserIdAndProcessingStatusOrderByCreatedAtDescIdDesc(
            UUID userId,
            String processingStatus,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CaptureEntity> findLockedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CaptureEntity> findLockedByIdAndUserId(UUID id, UUID userId);
}

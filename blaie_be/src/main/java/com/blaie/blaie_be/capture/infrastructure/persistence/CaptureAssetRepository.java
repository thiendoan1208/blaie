package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptureAssetRepository extends JpaRepository<CaptureAssetEntity, UUID> {
    List<CaptureAssetEntity> findByCaptureIdOrderByAssetPositionAsc(UUID captureId);

    Optional<CaptureAssetEntity> findByIdAndCaptureId(UUID id, UUID captureId);

    boolean existsByObjectKey(String objectKey);
}

package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptureItemRepository extends JpaRepository<CaptureItemEntity, UUID> {
    List<CaptureItemEntity> findByCaptureIdOrderByItemPositionAsc(UUID captureId);

    void deleteByCaptureId(UUID captureId);
}

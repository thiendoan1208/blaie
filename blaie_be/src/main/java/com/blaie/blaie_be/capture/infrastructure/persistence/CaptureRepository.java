package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptureRepository extends JpaRepository<CaptureEntity, UUID> {
}

package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureAssetEntity;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureAssetRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageReferenceIntegritySchedulerTest {
    @Test
    void reportsDatabaseAssetsWhosePrivateObjectIsMissing() {
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        CaptureAssetRepository assets = mock(CaptureAssetRepository.class);
        CaptureTelemetryPort telemetry = mock(CaptureTelemetryPort.class);
        StorageDeletionProperties properties = new StorageDeletionProperties();
        properties.setReferenceScanEnabled(true);
        properties.setReferenceScanBatchSize(10);
        CaptureAssetEntity existing = asset("captures/existing.png");
        CaptureAssetEntity missing = asset("captures/missing.png");
        when(assets.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing, missing)));
        when(storage.exists("captures/existing.png")).thenReturn(true);
        when(storage.exists("captures/missing.png")).thenReturn(false);

        new StorageReferenceIntegrityScheduler(
                storage,
                assets,
                properties,
                telemetry
        ).scan();

        verify(telemetry).incrementStorageReferencesMissing(1);
    }

    private CaptureAssetEntity asset(String objectKey) {
        CaptureAssetEntity asset = mock(CaptureAssetEntity.class);
        when(asset.objectKey()).thenReturn(objectKey);
        return asset;
    }
}

package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.application.port.StorageDeletionQueuePort;
import com.blaie.blaie_be.capture.application.port.StoredObject;
import com.blaie.blaie_be.capture.application.port.StoredObjectPage;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureAssetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageOrphanCleanupSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-07-28T20:00:00Z");

    @Test
    void queuesOnlyUnreferencedObjectsOlderThanTheSafetyWindowAndAdvancesPages() {
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        CaptureAssetRepository assets = mock(CaptureAssetRepository.class);
        StorageDeletionQueuePort deletionQueue = mock(StorageDeletionQueuePort.class);
        StorageDeletionProperties properties = new StorageDeletionProperties();
        properties.setOrphanScanEnabled(true);
        properties.setOrphanMinAge(java.time.Duration.ofHours(1));
        properties.setOrphanScanBatchSize(10);
        when(storage.list("captures/", null, 10)).thenReturn(new StoredObjectPage(
                List.of(
                        new StoredObject("captures/referenced.png", NOW.minusSeconds(7_200)),
                        new StoredObject("captures/orphan.png", NOW.minusSeconds(7_200)),
                        new StoredObject("captures/new-upload.png", NOW.minusSeconds(60))
                ),
                "next-page"
        ));
        when(storage.list("captures/", "next-page", 10))
                .thenReturn(new StoredObjectPage(List.of(), null));
        when(assets.existsByObjectKey("captures/referenced.png")).thenReturn(true);

        StorageOrphanCleanupScheduler scheduler = new StorageOrphanCleanupScheduler(
                storage,
                assets,
                deletionQueue,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        scheduler.scan();
        scheduler.scan();

        verify(deletionQueue).enqueue("captures/orphan.png", NOW);
        verify(deletionQueue, never()).enqueue("captures/referenced.png", NOW);
        verify(deletionQueue, never()).enqueue("captures/new-upload.png", NOW);
        verify(storage).list("captures/", "next-page", 10);
    }
}

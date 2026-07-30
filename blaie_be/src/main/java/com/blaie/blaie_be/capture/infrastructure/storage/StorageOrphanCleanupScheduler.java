package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.application.port.StorageDeletionQueuePort;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureAssetRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StorageOrphanCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(StorageOrphanCleanupScheduler.class);
    private static final String CAPTURE_PREFIX = "captures/";

    private final ObjectStoragePort storage;
    private final CaptureAssetRepository assets;
    private final StorageDeletionQueuePort deletionQueue;
    private final StorageDeletionProperties properties;
    private final Clock clock;
    private String continuationToken;

    public StorageOrphanCleanupScheduler(
            ObjectStoragePort storage,
            CaptureAssetRepository assets,
            StorageDeletionQueuePort deletionQueue,
            StorageDeletionProperties properties,
            Clock clock
    ) {
        this.storage = storage;
        this.assets = assets;
        this.deletionQueue = deletionQueue;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${blaie.storage.deletion.orphan-scan-interval:1h}")
    public synchronized void scan() {
        if (!properties.orphanScanEnabled()) {
            return;
        }
        try {
            Instant now = clock.instant();
            Instant cutoff = now.minus(properties.orphanMinAge());
            var page = storage.list(
                    CAPTURE_PREFIX,
                    continuationToken,
                    properties.orphanScanBatchSize()
            );
            page.objects().stream()
                    .filter(object -> !object.lastModified().isAfter(cutoff))
                    .filter(object -> !assets.existsByObjectKey(object.objectKey()))
                    .forEach(object -> deletionQueue.enqueue(object.objectKey(), now));
            continuationToken = page.nextContinuationToken();
        } catch (RuntimeException exception) {
            log.warn("Storage orphan scan failed: {}", exception.getClass().getSimpleName());
        }
    }
}

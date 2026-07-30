package com.blaie.blaie_be.capture.infrastructure.storage;

import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.ObjectStoragePort;
import com.blaie.blaie_be.capture.infrastructure.persistence.CaptureAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StorageReferenceIntegrityScheduler {
    private static final Logger log =
            LoggerFactory.getLogger(StorageReferenceIntegrityScheduler.class);

    private final ObjectStoragePort storage;
    private final CaptureAssetRepository assets;
    private final StorageDeletionProperties properties;
    private final CaptureTelemetryPort telemetry;
    private int pageIndex;

    public StorageReferenceIntegrityScheduler(
            ObjectStoragePort storage,
            CaptureAssetRepository assets,
            StorageDeletionProperties properties,
            CaptureTelemetryPort telemetry
    ) {
        this.storage = storage;
        this.assets = assets;
        this.properties = properties;
        this.telemetry = telemetry;
    }

    @Scheduled(fixedDelayString = "${blaie.storage.deletion.reference-scan-interval:1h}")
    public synchronized void scan() {
        if (!properties.referenceScanEnabled()) {
            return;
        }
        try {
            var page = assets.findAll(PageRequest.of(
                    pageIndex,
                    properties.referenceScanBatchSize(),
                    Sort.by(Sort.Direction.ASC, "id")
            ));
            long missing = page.getContent().stream()
                    .filter(asset -> !storage.exists(asset.objectKey()))
                    .count();
            telemetry.incrementStorageReferencesMissing(missing);
            pageIndex = page.hasNext() ? pageIndex + 1 : 0;
        } catch (RuntimeException exception) {
            log.warn(
                    "Storage reference integrity scan failed: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}

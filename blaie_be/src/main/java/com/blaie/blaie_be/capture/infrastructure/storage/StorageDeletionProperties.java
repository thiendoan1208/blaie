package com.blaie.blaie_be.capture.infrastructure.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blaie.storage.deletion")
public class StorageDeletionProperties {
    private boolean enabled = true;
    private int batchSize = 25;
    private int maxAttempts = 8;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private boolean orphanScanEnabled;
    private Duration orphanMinAge = Duration.ofHours(1);
    private int orphanScanBatchSize = 500;
    private boolean referenceScanEnabled;
    private int referenceScanBatchSize = 100;

    public boolean enabled() {
        return enabled;
    }

    public int batchSize() {
        return batchSize;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration leaseDuration() {
        return leaseDuration;
    }

    public boolean orphanScanEnabled() {
        return orphanScanEnabled;
    }

    public Duration orphanMinAge() {
        return orphanMinAge;
    }

    public int orphanScanBatchSize() {
        return orphanScanBatchSize;
    }

    public boolean referenceScanEnabled() {
        return referenceScanEnabled;
    }

    public int referenceScanBatchSize() {
        return referenceScanBatchSize;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public void setOrphanScanEnabled(boolean orphanScanEnabled) {
        this.orphanScanEnabled = orphanScanEnabled;
    }

    public void setOrphanMinAge(Duration orphanMinAge) {
        this.orphanMinAge = orphanMinAge;
    }

    public void setOrphanScanBatchSize(int orphanScanBatchSize) {
        this.orphanScanBatchSize = orphanScanBatchSize;
    }

    public void setReferenceScanEnabled(boolean referenceScanEnabled) {
        this.referenceScanEnabled = referenceScanEnabled;
    }

    public void setReferenceScanBatchSize(int referenceScanBatchSize) {
        this.referenceScanBatchSize = referenceScanBatchSize;
    }
}

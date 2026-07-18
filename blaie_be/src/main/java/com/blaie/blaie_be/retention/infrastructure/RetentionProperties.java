package com.blaie.blaie_be.retention.infrastructure;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.retention")
public class RetentionProperties {
    private boolean enabled = true;
    private Duration cleanupInterval = Duration.ofHours(1);
    private int batchSize = 500;
    private int maxBatchesPerRun = 10;
    private Duration completedOutbox = Duration.ofDays(7);
    private Duration completedProcessingJobs = Duration.ofDays(90);
    private Duration auditEvents = Duration.ofDays(365);

    public boolean enabled() { return enabled; }

    public Duration cleanupInterval() { return cleanupInterval; }

    public int batchSize() { return batchSize; }

    public int maxBatchesPerRun() { return maxBatchesPerRun; }

    public Duration completedOutbox() { return completedOutbox; }

    public Duration completedProcessingJobs() { return completedProcessingJobs; }

    public Duration auditEvents() { return auditEvents; }

    @AssertTrue(message = "retention configuration must use positive durations and batch limits")
    public boolean isConfigurationValid() {
        return cleanupInterval != null && cleanupInterval.isPositive()
                && batchSize > 0
                && maxBatchesPerRun > 0
                && completedOutbox != null && completedOutbox.isPositive()
                && completedProcessingJobs != null && completedProcessingJobs.isPositive()
                && auditEvents != null && auditEvents.isPositive();
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }

    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public void setMaxBatchesPerRun(int maxBatchesPerRun) { this.maxBatchesPerRun = maxBatchesPerRun; }

    public void setCompletedOutbox(Duration completedOutbox) { this.completedOutbox = completedOutbox; }

    public void setCompletedProcessingJobs(Duration completedProcessingJobs) {
        this.completedProcessingJobs = completedProcessingJobs;
    }

    public void setAuditEvents(Duration auditEvents) { this.auditEvents = auditEvents; }
}

package com.blaie.blaie_be.capture.infrastructure.observability;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.capture.observability")
public class CaptureObservabilityProperties {
    private boolean collectorEnabled = true;
    private Duration snapshotInterval = Duration.ofSeconds(15);
    private int schedulerPoolSize = 1;
    private int maxProviderTagValues = 10;

    public boolean collectorEnabled() {
        return collectorEnabled;
    }

    public Duration snapshotInterval() {
        return snapshotInterval;
    }

    public int schedulerPoolSize() {
        return schedulerPoolSize;
    }

    public int maxProviderTagValues() {
        return maxProviderTagValues;
    }

    @AssertTrue(message = "capture observability durations and limits must be positive")
    public boolean isConfigurationValid() {
        return snapshotInterval != null
                && snapshotInterval.toMillis() > 0
                && schedulerPoolSize > 0
                && maxProviderTagValues > 0;
    }

    public void setCollectorEnabled(boolean collectorEnabled) {
        this.collectorEnabled = collectorEnabled;
    }

    public void setSnapshotInterval(Duration snapshotInterval) {
        this.snapshotInterval = snapshotInterval;
    }

    public void setSchedulerPoolSize(int schedulerPoolSize) {
        this.schedulerPoolSize = schedulerPoolSize;
    }

    public void setMaxProviderTagValues(int maxProviderTagValues) {
        this.maxProviderTagValues = maxProviderTagValues;
    }
}

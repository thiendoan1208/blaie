package com.blaie.blaie_be.capture.infrastructure.observability;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureObservabilityPropertiesTest {
    @Test
    void defaultsAreSafeAndValid() {
        CaptureObservabilityProperties properties = new CaptureObservabilityProperties();

        assertThat(properties.collectorEnabled()).isTrue();
        assertThat(properties.snapshotInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.schedulerPoolSize()).isEqualTo(1);
        assertThat(properties.maxProviderTagValues()).isEqualTo(10);
        assertThat(properties.isConfigurationValid()).isTrue();
    }

    @Test
    void rejectsNonPositiveSchedulingAndCardinalityValues() {
        CaptureObservabilityProperties properties = new CaptureObservabilityProperties();
        properties.setSnapshotInterval(Duration.ZERO);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setSnapshotInterval(Duration.ofSeconds(1));
        properties.setSchedulerPoolSize(0);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setSchedulerPoolSize(1);
        properties.setMaxProviderTagValues(0);
        assertThat(properties.isConfigurationValid()).isFalse();
    }
}

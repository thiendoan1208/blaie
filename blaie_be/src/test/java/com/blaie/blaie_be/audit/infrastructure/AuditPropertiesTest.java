package com.blaie.blaie_be.audit.infrastructure;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPropertiesTest {
    @Test
    void rejectsSubSecondDeduplicationWindows() {
        AuditProperties properties = new AuditProperties();
        properties.setReadDeduplicationWindow(Duration.ofMillis(999));

        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setReadDeduplicationWindow(Duration.ofSeconds(1));
        assertThat(properties.isConfigurationValid()).isTrue();
    }
}

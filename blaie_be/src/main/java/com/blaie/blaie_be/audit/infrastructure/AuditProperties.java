package com.blaie.blaie_be.audit.infrastructure;

import com.blaie.blaie_be.audit.application.port.AuditSettingsPort;
import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.audit")
public class AuditProperties implements AuditSettingsPort {
    private Duration readDeduplicationWindow = Duration.ofMinutes(15);

    @Override
    public Duration readDeduplicationWindow() {
        return readDeduplicationWindow;
    }

    @AssertTrue(message = "audit read deduplication window must be at least one second")
    public boolean isConfigurationValid() {
        return readDeduplicationWindow != null
                && readDeduplicationWindow.compareTo(Duration.ofSeconds(1)) >= 0;
    }

    public void setReadDeduplicationWindow(Duration readDeduplicationWindow) {
        this.readDeduplicationWindow = readDeduplicationWindow;
    }
}

package com.blaie.blaie_be.audit.application.port;

import java.time.Duration;

public interface AuditSettingsPort {
    Duration readDeduplicationWindow();
}

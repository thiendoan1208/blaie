package com.blaie.blaie_be.capture.application.port;

import java.time.Duration;

public interface CaptureProcessingSettingsPort {
    int maxAttempts();

    Duration idempotencyTtl();

    Duration leaseDuration();

    Duration heartbeatInterval();

    Duration retryDelay(int failedAttemptCount);
}

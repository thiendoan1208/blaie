package com.blaie.blaie_be.capture.application.port;

import java.time.Duration;

public interface CaptureProcessingSettingsPort {
    boolean acceptAsyncEnabled();

    int maxAttempts();

    Duration idempotencyTtl();

    Duration leaseDuration();

    Duration heartbeatInterval();

    Duration retryDelay(int failedAttemptCount);

    Duration dispatchRetryDelay(int dispatchGeneration);

    int maxActiveJobsPerUser();

    int maxActiveJobsTotal();

    Duration maxOldestQueuedAge();

    Duration admissionRetryAfter();
}

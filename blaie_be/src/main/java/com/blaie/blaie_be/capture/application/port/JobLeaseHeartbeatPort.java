package com.blaie.blaie_be.capture.application.port;

import java.util.UUID;

public interface JobLeaseHeartbeatPort {
    ActiveHeartbeat start(
            UUID jobId,
            String workerId,
            int attemptCount,
            int retryGeneration
    );

    @FunctionalInterface
    interface ActiveHeartbeat {
        void stop();
    }
}

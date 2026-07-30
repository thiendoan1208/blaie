package com.blaie.blaie_be.capture.application.port;

import java.util.UUID;

public interface CaptureJobDispatchPort {
    void publish(
            UUID jobId,
            UUID captureId,
            int dispatchGeneration,
            String originRequestId
    );
}

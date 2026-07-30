package com.blaie.blaie_be.capture.application.event;

import com.blaie.blaie_be.core.request.RequestIdPolicy;
import java.util.Objects;
import java.util.UUID;

public record CaptureJobQueuedEvent(
        UUID eventId,
        UUID jobId,
        UUID captureId,
        int dispatchGeneration,
        String originRequestId
) {
    public CaptureJobQueuedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(captureId, "captureId");
        if (dispatchGeneration < 1) {
            throw new IllegalArgumentException("dispatchGeneration must be positive");
        }
        if (!RequestIdPolicy.isValid(originRequestId)) {
            originRequestId = eventId.toString();
        }
    }
}

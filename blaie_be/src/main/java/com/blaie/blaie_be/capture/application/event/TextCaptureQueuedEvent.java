package com.blaie.blaie_be.capture.application.event;

import java.util.UUID;

public record TextCaptureQueuedEvent(
        UUID eventId,
        UUID jobId,
        UUID captureId,
        int dispatchGeneration
) {
}

package com.blaie.blaie_be.capture.application.result;

import java.util.UUID;

public record RecoveredJobResult(
        UUID jobId,
        UUID captureId,
        boolean dispatch
) {
}

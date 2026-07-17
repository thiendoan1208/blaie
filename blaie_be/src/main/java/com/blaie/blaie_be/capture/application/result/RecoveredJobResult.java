package com.blaie.blaie_be.capture.application.result;

import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.util.Objects;
import java.util.UUID;

public record RecoveredJobResult(
        UUID jobId,
        UUID captureId,
        RecoveryOutcome outcome,
        TextClassificationFailureClass failureClass
) {
    public RecoveredJobResult {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(captureId, "captureId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(failureClass, "failureClass");
    }

    public enum RecoveryOutcome {
        RETRY_SCHEDULED,
        DEAD
    }
}

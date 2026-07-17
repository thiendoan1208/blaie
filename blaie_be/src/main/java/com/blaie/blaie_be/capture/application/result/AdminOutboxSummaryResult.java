package com.blaie.blaie_be.capture.application.result;

import java.time.Instant;

public record AdminOutboxSummaryResult(
        long backlogCount,
        Instant oldestPublicationAt,
        long oldestAgeSeconds,
        Instant lastResubmissionAt,
        int maxCompletionAttempts
) {
}

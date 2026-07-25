package com.blaie.blaie_be.capture.api.admin.response;

import com.blaie.blaie_be.capture.application.admin.result.AdminOutboxSummaryResult;
import java.time.Instant;

public record AdminOutboxSummaryResponse(
        long backlogCount,
        Instant oldestPublicationAt,
        long oldestAgeSeconds,
        Instant lastResubmissionAt,
        int maxCompletionAttempts
) {
    public static AdminOutboxSummaryResponse from(AdminOutboxSummaryResult result) {
        return new AdminOutboxSummaryResponse(
                result.backlogCount(),
                result.oldestPublicationAt(),
                result.oldestAgeSeconds(),
                result.lastResubmissionAt(),
                result.maxCompletionAttempts()
        );
    }
}

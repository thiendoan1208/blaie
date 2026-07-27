package com.blaie.blaie_be.inbox.application.result;

import java.time.Instant;
import java.util.UUID;

public record InboxItemResult(
        UUID id,
        UUID captureId,
        String originalText,
        String category,
        String processingStatus,
        Instant createdAt
) {
}

package com.blaie.blaie_be.inbox.api.response;

import com.blaie.blaie_be.inbox.application.result.InboxItemResult;
import java.time.Instant;
import java.util.UUID;

public record InboxItemResponse(
        UUID id,
        UUID captureId,
        String originalText,
        String category,
        String processingStatus,
        Instant createdAt
) {
    public static InboxItemResponse from(InboxItemResult item) {
        return new InboxItemResponse(
                item.id(),
                item.captureId(),
                item.originalText(),
                item.category(),
                item.processingStatus(),
                item.createdAt()
        );
    }
}

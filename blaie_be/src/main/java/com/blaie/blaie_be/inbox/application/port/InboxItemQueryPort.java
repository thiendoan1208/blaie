package com.blaie.blaie_be.inbox.application.port;

import com.blaie.blaie_be.inbox.application.result.InboxItemResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InboxItemQueryPort {
    Optional<InboxItemResult> findOwned(UUID itemId, UUID userId);

    List<InboxItemResult> findFirstPage(UUID userId, int limit);

    List<InboxItemResult> findPageAfter(UUID userId, Instant createdAt, UUID itemId, int limit);
}

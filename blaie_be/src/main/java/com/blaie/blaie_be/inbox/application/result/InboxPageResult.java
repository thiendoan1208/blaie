package com.blaie.blaie_be.inbox.application.result;

import java.util.List;

public record InboxPageResult(
        List<InboxItemResult> items,
        String nextCursor,
        boolean hasMore,
        int limit
) {
    public InboxPageResult {
        items = List.copyOf(items);
    }
}

package com.blaie.blaie_be.capture.application.result;

import java.util.List;

public record InboxPageResult(
        List<CaptureItemResult> items,
        String nextCursor,
        boolean hasMore,
        int limit
) {
}

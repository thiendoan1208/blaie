package com.blaie.blaie_be.audit.application.result;

import com.blaie.blaie_be.audit.domain.AuditEvent;
import java.util.List;

public record AuditEventPageResult(
        List<AuditEvent> events,
        String nextCursor,
        boolean hasMore,
        int limit
) {
    public AuditEventPageResult {
        events = List.copyOf(events);
    }
}

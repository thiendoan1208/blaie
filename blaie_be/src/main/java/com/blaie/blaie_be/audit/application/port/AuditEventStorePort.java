package com.blaie.blaie_be.audit.application.port;

import com.blaie.blaie_be.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventStorePort {
    void append(AuditEvent event);

    List<AuditEvent> findPage(Instant cursorOccurredAt, UUID cursorId, int limit);
}

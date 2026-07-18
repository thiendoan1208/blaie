package com.blaie.blaie_be.audit.api.response;

import com.blaie.blaie_be.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        String actorId,
        boolean actorAdmin,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        String requestId,
        Instant occurredAt
) {
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.id(),
                event.actorId(),
                event.actorAdmin(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.outcome().value(),
                event.requestId(),
                event.occurredAt()
        );
    }
}

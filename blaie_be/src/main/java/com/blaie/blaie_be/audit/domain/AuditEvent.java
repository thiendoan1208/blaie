package com.blaie.blaie_be.audit.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        String actorId,
        boolean actorAdmin,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        String requestId,
        Instant occurredAt,
        Instant deduplicationBucket
) {
    public AuditEvent {
        Objects.requireNonNull(id, "id");
        new AuditAccess(action, resourceType, resourceId);
        requireSafe(actorId, "actorId");
        requireSafe(requestId, "requestId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static void requireSafe(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}

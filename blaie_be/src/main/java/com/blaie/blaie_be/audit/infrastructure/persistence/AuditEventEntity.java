package com.blaie.blaie_be.audit.infrastructure.persistence;

import com.blaie.blaie_be.audit.domain.AuditEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {
    @Id
    private UUID id;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "actor_admin", nullable = false)
    private boolean actorAdmin;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "deduplication_bucket")
    private Instant deduplicationBucket;

    protected AuditEventEntity() {
    }

    static AuditEventEntity from(AuditEvent event) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.id = event.id();
        entity.actorId = event.actorId();
        entity.actorAdmin = event.actorAdmin();
        entity.action = event.action();
        entity.resourceType = event.resourceType();
        entity.resourceId = event.resourceId();
        entity.outcome = event.outcome().value();
        entity.requestId = event.requestId();
        entity.occurredAt = event.occurredAt();
        entity.deduplicationBucket = event.deduplicationBucket();
        return entity;
    }

    UUID id() {
        return id;
    }

    String actorId() {
        return actorId;
    }

    boolean actorAdmin() {
        return actorAdmin;
    }

    String action() {
        return action;
    }

    String resourceType() {
        return resourceType;
    }

    String resourceId() {
        return resourceId;
    }

    String outcome() {
        return outcome;
    }

    String requestId() {
        return requestId;
    }

    Instant occurredAt() {
        return occurredAt;
    }

    Instant deduplicationBucket() {
        return deduplicationBucket;
    }
}

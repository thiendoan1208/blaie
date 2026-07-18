package com.blaie.blaie_be.audit.infrastructure.persistence;

import com.blaie.blaie_be.audit.application.port.AuditEventStorePort;
import com.blaie.blaie_be.audit.domain.AuditEvent;
import com.blaie.blaie_be.audit.domain.AuditOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;

@Component
public class JpaAuditEventStoreAdapter implements AuditEventStorePort {
    private final AuditEventRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public JpaAuditEventStoreAdapter(AuditEventRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void append(AuditEvent event) {
        jdbcTemplate.update("""
                INSERT INTO audit_events (
                    id, actor_id, actor_admin, action, resource_type, resource_id,
                    outcome, request_id, occurred_at, deduplication_bucket
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                event.id(), event.actorId(), event.actorAdmin(), event.action(),
                event.resourceType(), event.resourceId(), event.outcome().value(),
                event.requestId(), Timestamp.from(event.occurredAt()),
                event.deduplicationBucket() == null ? null : Timestamp.from(event.deduplicationBucket())
        );
    }

    @Override
    public List<AuditEvent> findPage(Instant cursorOccurredAt, UUID cursorId, int limit) {
        return repository.findPage(cursorOccurredAt, cursorId, PageRequest.of(0, limit)).stream()
                .map(entity -> new AuditEvent(
                        entity.id(),
                        entity.actorId(),
                        entity.actorAdmin(),
                        entity.action(),
                        entity.resourceType(),
                        entity.resourceId(),
                        AuditOutcome.valueOf(entity.outcome().toUpperCase()),
                        entity.requestId(),
                        entity.occurredAt(),
                        entity.deduplicationBucket()
                ))
                .toList();
    }
}

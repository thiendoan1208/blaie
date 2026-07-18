package com.blaie.blaie_be.audit.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    @Query(value = """
            SELECT * FROM audit_events
            WHERE (
                CAST(:cursorOccurredAt AS TIMESTAMPTZ) IS NULL
                OR occurred_at < :cursorOccurredAt
                OR (occurred_at = :cursorOccurredAt AND id < :cursorId)
            )
            ORDER BY occurred_at DESC, id DESC
            """, nativeQuery = true)
    List<AuditEventEntity> findPage(
            @Param("cursorOccurredAt") Instant cursorOccurredAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}

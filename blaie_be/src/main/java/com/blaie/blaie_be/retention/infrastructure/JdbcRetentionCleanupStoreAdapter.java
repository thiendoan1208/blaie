package com.blaie.blaie_be.retention.infrastructure;

import com.blaie.blaie_be.retention.application.port.RetentionCleanupStorePort;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcRetentionCleanupStoreAdapter implements RetentionCleanupStorePort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRetentionCleanupStoreAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public int deleteExpiredIdempotencyKeys(Instant now, int batchSize) {
        return jdbcTemplate.update("""
                WITH candidates AS MATERIALIZED (
                    SELECT user_id, idempotency_key
                    FROM capture_idempotency_keys
                    WHERE expires_at <= ?
                    ORDER BY expires_at, user_id, idempotency_key
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                DELETE FROM capture_idempotency_keys target
                USING candidates
                WHERE target.user_id = candidates.user_id
                  AND target.idempotency_key = candidates.idempotency_key
                """, Timestamp.from(now), batchSize);
    }

    @Override
    @Transactional
    public int deleteCompletedOutboxEvents(Instant cutoff, int batchSize) {
        return jdbcTemplate.update("""
                WITH candidates AS MATERIALIZED (
                    SELECT id
                    FROM event_publication
                    WHERE completion_date IS NOT NULL
                      AND completion_date < ?
                    ORDER BY completion_date, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                DELETE FROM event_publication target
                USING candidates
                WHERE target.id = candidates.id
                """, Timestamp.from(cutoff), batchSize);
    }

    @Override
    @Transactional
    public int deleteCompletedProcessingJobs(Instant cutoff, int batchSize) {
        return jdbcTemplate.update("""
                WITH candidates AS MATERIALIZED (
                    SELECT id
                    FROM processing_jobs
                    WHERE status = 'completed'
                      AND completed_at IS NOT NULL
                      AND completed_at < ?
                    ORDER BY completed_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                DELETE FROM processing_jobs target
                USING candidates
                WHERE target.id = candidates.id
                  AND target.status = 'completed'
                  AND target.completed_at < ?
                """, Timestamp.from(cutoff), batchSize, Timestamp.from(cutoff));
    }

    @Override
    @Transactional
    public int deleteExpiredAuditEvents(Instant cutoff, int batchSize) {
        return jdbcTemplate.update("""
                WITH candidates AS MATERIALIZED (
                    SELECT id
                    FROM audit_events
                    WHERE occurred_at < ?
                    ORDER BY occurred_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                DELETE FROM audit_events target
                USING candidates
                WHERE target.id = candidates.id
                  AND target.occurred_at < ?
                """, Timestamp.from(cutoff), batchSize, Timestamp.from(cutoff));
    }
}

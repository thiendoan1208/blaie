package com.blaie.blaie_be.capture.infrastructure.observability;

import com.blaie.blaie_be.capture.application.event.CaptureJobQueuedEvent;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CaptureOperationalSnapshotReader {
    static final String JOB_SNAPSHOT_SQL = """
            SELECT COUNT(*) FILTER (WHERE status = 'queued') AS queued_count,
                   COUNT(*) FILTER (WHERE status = 'retry_wait') AS retry_wait_count,
                   COUNT(*) FILTER (WHERE status = 'processing') AS processing_count,
                   COUNT(*) FILTER (
                       WHERE status = 'processing'
                         AND lease_expires_at > CURRENT_TIMESTAMP
                   ) AS active_lease_count,
                   MIN(available_at) FILTER (WHERE status = 'queued') AS oldest_queued_at
            FROM processing_jobs
            WHERE status IN ('queued', 'retry_wait', 'processing')
            """;
    static final String OUTBOX_SNAPSHOT_SQL = """
            SELECT COUNT(*) AS backlog_count,
                   MIN(publication_date) AS oldest_publication_at
            FROM event_publication
            WHERE completion_date IS NULL
              AND event_type = ?
              AND listener_id = 'capture-job-redis-publisher'
            """;
    static final String STORAGE_DELETION_SNAPSHOT_SQL = """
            SELECT COUNT(*) FILTER (
                       WHERE status IN ('pending', 'retry_wait')
                         AND attempt_count < max_attempts
                   ) AS ready_count,
                   COUNT(*) FILTER (WHERE status = 'processing') AS processing_count,
                   COUNT(*) FILTER (
                       WHERE status = 'retry_wait'
                         AND attempt_count >= max_attempts
                   ) AS exhausted_count
            FROM storage_deletion_jobs
            """;

    private final JdbcTemplate jdbcTemplate;

    public CaptureOperationalSnapshotReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JobSnapshot readJobs() {
        return jdbcTemplate.queryForObject(JOB_SNAPSHOT_SQL, (resultSet, rowNumber) -> new JobSnapshot(
                resultSet.getLong("queued_count"),
                resultSet.getLong("retry_wait_count"),
                resultSet.getLong("processing_count"),
                resultSet.getLong("active_lease_count"),
                instant(resultSet.getTimestamp("oldest_queued_at"))
        ));
    }

    public OutboxSnapshot readOutbox() {
        return jdbcTemplate.queryForObject(
                OUTBOX_SNAPSHOT_SQL,
                (resultSet, rowNumber) -> new OutboxSnapshot(
                        resultSet.getLong("backlog_count"),
                        instant(resultSet.getTimestamp("oldest_publication_at"))
                ),
                CaptureJobQueuedEvent.class.getName()
        );
    }

    public StorageDeletionSnapshot readStorageDeletions() {
        return jdbcTemplate.queryForObject(
                STORAGE_DELETION_SNAPSHOT_SQL,
                (resultSet, rowNumber) -> new StorageDeletionSnapshot(
                        resultSet.getLong("ready_count"),
                        resultSet.getLong("processing_count"),
                        resultSet.getLong("exhausted_count")
                )
        );
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record JobSnapshot(
            long queued,
            long retryWait,
            long processing,
            long activeLeases,
            Instant oldestQueuedAt
    ) {
    }

    public record OutboxSnapshot(long backlog, Instant oldestPublicationAt) {
    }

    public record StorageDeletionSnapshot(long ready, long processing, long exhausted) {
    }
}

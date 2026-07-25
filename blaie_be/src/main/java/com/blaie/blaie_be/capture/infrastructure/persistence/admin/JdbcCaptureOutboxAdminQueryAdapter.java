package com.blaie.blaie_be.capture.infrastructure.persistence.admin;

import com.blaie.blaie_be.capture.application.admin.port.CaptureOutboxAdminQueryPort;
import com.blaie.blaie_be.capture.application.admin.result.AdminOutboxSummaryResult;
import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcCaptureOutboxAdminQueryAdapter implements CaptureOutboxAdminQueryPort {
    private static final String CAPTURE_PUBLISHER_LISTENER = "capture-text-job-redis-publisher";
    private static final String CAPTURE_EVENT_TYPE = TextCaptureQueuedEvent.class.getName();

    private final JdbcTemplate jdbcTemplate;

    public JdbcCaptureOutboxAdminQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOutboxSummaryResult outboxSummary(Instant now) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS backlog_count,
                       MIN(publication_date) AS oldest_publication_at,
                       MAX(last_resubmission_date) AS last_resubmission_at,
                       COALESCE(MAX(completion_attempts), 0) AS max_completion_attempts
                  FROM event_publication
                 WHERE event_type = ?
                   AND listener_id = ?
                   AND completion_date IS NULL
                """,
                (resultSet, rowNumber) -> {
                    Instant oldest = instant(resultSet.getObject(
                            "oldest_publication_at",
                            OffsetDateTime.class
                    ));
                    Instant lastResubmission = instant(resultSet.getObject(
                            "last_resubmission_at",
                            OffsetDateTime.class
                    ));
                    return new AdminOutboxSummaryResult(
                            resultSet.getLong("backlog_count"),
                            oldest,
                            oldest == null ? 0 : Math.max(0, Duration.between(oldest, now).toSeconds()),
                            lastResubmission,
                            resultSet.getInt("max_completion_attempts")
                    );
                },
                CAPTURE_EVENT_TYPE,
                CAPTURE_PUBLISHER_LISTENER
        );
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}

package com.blaie.blaie_be.inbox.infrastructure.persistence;

import com.blaie.blaie_be.inbox.application.port.InboxItemQueryPort;
import com.blaie.blaie_be.inbox.application.result.InboxItemResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcInboxItemQueryAdapter implements InboxItemQueryPort {
    private static final String SELECT_COLUMNS = """
            SELECT id, capture_id, original_text, category, processing_status, created_at
              FROM capture_items
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcInboxItemQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InboxItemResult> findOwned(UUID itemId, UUID userId) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE id = ? AND user_id = ?",
                this::toResult,
                itemId,
                userId
        ).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InboxItemResult> findFirstPage(UUID userId, int limit) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + """
                         WHERE user_id = ?
                         ORDER BY created_at DESC, id DESC
                         LIMIT ?
                        """,
                this::toResult,
                userId,
                limit
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InboxItemResult> findPageAfter(
            UUID userId,
            Instant createdAt,
            UUID itemId,
            int limit
    ) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + """
                         WHERE user_id = ?
                           AND (created_at < ? OR (created_at = ? AND id < ?))
                         ORDER BY created_at DESC, id DESC
                         LIMIT ?
                        """,
                this::toResult,
                userId,
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                itemId,
                limit
        );
    }

    private InboxItemResult toResult(ResultSet resultSet, int rowNumber) throws SQLException {
        return new InboxItemResult(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("capture_id", UUID.class),
                resultSet.getString("original_text"),
                resultSet.getString("category"),
                resultSet.getString("processing_status"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}

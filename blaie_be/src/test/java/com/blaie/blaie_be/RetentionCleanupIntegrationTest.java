package com.blaie.blaie_be;

import com.blaie.blaie_be.retention.application.port.RetentionCleanupStorePort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "blaie.retention.enabled=false",
        "blaie.capture.processing.accept-async-enabled=false",
        "blaie.capture.processing.publisher-enabled=false",
        "blaie.capture.processing.worker-enabled=false",
        "blaie.capture.processing.recovery-enabled=false",
        "blaie.auth.access-token-secret=retention-test-access-secret-at-least-32-bytes",
        "blaie.email.provider=log",
        "blaie.email.verification-ttl=24h",
        "blaie.email.from=Blaie <no-reply@test.local>",
        "blaie.email.web-base-url=http://localhost:3000",
        "blaie.email.api-base-url=http://localhost:8080/api/v1",
        "blaie.google.oauth.client-id=test-google-client-id",
        "blaie.google.oauth.client-secret=test-google-client-secret",
        "blaie.google.oauth.redirect-uri=http://localhost:8080/api/v1/auth/google/callback",
        "blaie.google.oauth.web-base-url=http://localhost:3000"
})
class RetentionCleanupIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Autowired
    private RetentionCleanupStorePort store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("delete from audit_events");
        jdbcTemplate.execute("delete from event_publication");
        jdbcTemplate.execute("delete from capture_idempotency_keys");
        jdbcTemplate.execute("delete from processing_jobs");
        jdbcTemplate.execute("delete from capture_items");
        jdbcTemplate.execute("delete from captures");
        jdbcTemplate.execute("delete from auth_action_tokens");
        jdbcTemplate.execute("delete from refresh_tokens");
        jdbcTemplate.execute("delete from auth_identities");
        jdbcTemplate.execute("delete from users");
    }

    @Test
    void deletesOnlyExpiredOperationalMetadataInBoundedBatches() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("insert into users (id, display_name) values (?, 'Retention User')", userId);
        UUID completedCapture = capture(userId, "completed", null);
        UUID deadCapture = capture(userId, "failed", "ai_provider_unavailable");
        UUID activeCapture = capture(userId, "processing", null);
        UUID freshCapture = capture(userId, "completed", null);

        job(completedCapture, userId, "completed", NOW.minusSeconds(100));
        job(deadCapture, userId, "dead", NOW.minusSeconds(100));
        job(activeCapture, userId, "queued", null);
        job(freshCapture, userId, "completed", NOW.minusSeconds(5));
        idempotency(userId, completedCapture, NOW.minusSeconds(60), NOW);
        idempotency(userId, freshCapture, NOW.minusSeconds(5), NOW.plusSeconds(60));
        outbox(NOW.minusSeconds(100), NOW.minusSeconds(100));
        outbox(NOW.minusSeconds(100), NOW.minusSeconds(5));
        outbox(NOW.minusSeconds(100), null);
        audit(NOW.minusSeconds(100));
        audit(NOW.minusSeconds(5));

        assertThat(store.deleteExpiredIdempotencyKeys(NOW, 1)).isEqualTo(1);
        assertThat(store.deleteCompletedOutboxEvents(NOW.minusSeconds(30), 1)).isEqualTo(1);
        assertThat(store.deleteCompletedProcessingJobs(NOW.minusSeconds(30), 1)).isEqualTo(1);
        assertThat(store.deleteExpiredAuditEvents(NOW.minusSeconds(30), 1)).isEqualTo(1);

        assertThat(count("capture_idempotency_keys")).isEqualTo(1);
        assertThat(countWhere("event_publication", "completion_date is null")).isEqualTo(1);
        assertThat(count("event_publication")).isEqualTo(2);
        assertThat(countWhere("processing_jobs", "status = 'dead'")).isEqualTo(1);
        assertThat(countWhere("processing_jobs", "status = 'queued'")).isEqualTo(1);
        assertThat(countWhere("processing_jobs", "status = 'completed'")).isEqualTo(1);
        assertThat(count("audit_events")).isEqualTo(1);
        assertThat(count("captures")).isEqualTo(4);
    }

    private UUID capture(UUID userId, String status, String failureCode) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into captures (id, user_id, original_text, processing_status, failure_code)
                values (?, ?, 'retention text', ?, ?)
                """, id, userId, status, failureCode);
        return id;
    }

    private void job(UUID captureId, UUID userId, String status, Instant completedAt) {
        UUID id = UUID.randomUUID();
        String error = "dead".equals(status) ? "ai_provider_unavailable" : null;
        String failureClass = "dead".equals(status) ? "provider_retryable" : null;
        jdbcTemplate.update("""
                insert into processing_jobs (
                    id, capture_id, user_id, job_type, status, completed_at,
                    last_error_code, last_failure_class, origin_request_id
                ) values (?, ?, ?, 'text_classification', ?, ?, ?, ?, ?)
                """,
                id, captureId, userId, status,
                completedAt == null ? null : Timestamp.from(completedAt),
                error, failureClass, "retention-" + id);
    }

    private void idempotency(UUID userId, UUID captureId, Instant createdAt, Instant expiresAt) {
        jdbcTemplate.update("""
                insert into capture_idempotency_keys (
                    user_id, idempotency_key, request_hash, capture_id, created_at, expires_at
                ) values (?, ?, ?, ?, ?, ?)
                """, userId, UUID.randomUUID(), "a".repeat(64), captureId,
                Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    private void outbox(Instant publicationAt, Instant completionAt) {
        jdbcTemplate.update("""
                insert into event_publication (
                    id, publication_date, listener_id, serialized_event, event_type,
                    completion_date, status, completion_attempts
                ) values (?, ?, 'retention-listener', '{}', 'retention-event', ?, 'PUBLISHED', 0)
                """, UUID.randomUUID(), Timestamp.from(publicationAt),
                completionAt == null ? null : Timestamp.from(completionAt));
    }

    private void audit(Instant occurredAt) {
        jdbcTemplate.update("""
                insert into audit_events (
                    id, actor_id, actor_admin, action, resource_type,
                    outcome, request_id, occurred_at
                ) values (?, ?, false, 'inbox.list', 'inbox', 'success', ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID().toString(),
                "retention-request", Timestamp.from(occurredAt));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private int countWhere(String table, String condition) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + condition,
                Integer.class
        );
    }
}

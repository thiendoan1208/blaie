package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class CaptureObservabilityMigrationUpgradeTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:latest"));

    static {
        POSTGRES.withDatabaseName("capture_observability_upgrade");
        POSTGRES.withUsername("capture_test");
        POSTGRES.withPassword("capture_test");
    }

    @Test
    void upgradesSeededV14DatabaseWithoutLosingJobOrOutboxState() throws Exception {
        Flyway v14Flyway = flywayAt(MigrationVersion.fromVersion("14"));
        MigrateResult v14Result = v14Flyway.migrate();

        assertEquals("14", v14Result.targetSchemaVersion);
        assertFalse(columnExists("processing_jobs", "origin_request_id"));

        SeedData seed = seedV14Data();
        Flyway latestFlyway = flywayAt(null);
        MigrateResult latestResult = latestFlyway.migrate();

        assertEquals(3, latestResult.migrationsExecuted);
        assertEquals("17", latestResult.targetSchemaVersion);
        assertSeededCaptureWasPreserved(seed);
        assertSeededJobWasPreservedAndBackfilled(seed);
        assertSeededOutboxEventWasPreserved(seed);
        assertOriginRequestIdSchemaAndDefault(seed);
        assertObservabilityAndAdminIndexesExist();
        assertPrivacyRetentionSchemaExists();
        latestFlyway.validate();
    }

    private void assertSeededCaptureWasPreserved(SeedData seed) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                    SELECT user_id, original_text, processing_status, failure_code,
                           created_at, updated_at
                    FROM captures
                    WHERE id = ?
                    """)) {
            statement.setObject(1, seed.captureId());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(seed.userId(), result.getObject("user_id", UUID.class));
                assertEquals("Seeded upgrade text", result.getString("original_text"));
                assertEquals("processing", result.getString("processing_status"));
                assertNull(result.getString("failure_code"));
                assertEquals(seed.createdAt(), result.getObject("created_at", OffsetDateTime.class));
                assertEquals(seed.createdAt(), result.getObject("updated_at", OffsetDateTime.class));
                assertFalse(result.next());
            }
        }
    }

    private Flyway flywayAt(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(MIGRATION_LOCATION);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private SeedData seedV14Data() throws SQLException {
        UUID userId = UUID.randomUUID();
        UUID captureId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 7, 16, 12, 30, 0, 0, ZoneOffset.UTC);
        OffsetDateTime availableAt = createdAt.plusMinutes(2);
        OffsetDateTime leaseExpiresAt = createdAt.plusMinutes(10);
        String serializedEvent = """
                {"eventId":"%s","jobId":"%s","captureId":"%s","dispatchGeneration":3}
                """.formatted(eventId, jobId, captureId).strip();

        try (Connection connection = connection()) {
            insertUser(connection, userId);
            insertCapture(connection, captureId, userId, "Seeded upgrade text", createdAt);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO processing_jobs (
                        id, capture_id, user_id, job_type, status,
                        attempt_count, max_attempts, retry_generation, available_at,
                        lease_owner, lease_expires_at, last_error_code,
                        created_at, updated_at, completed_at,
                        dispatch_generation, last_dispatched_at, next_dispatch_at,
                        last_failure_class
                    ) VALUES (?, ?, ?, 'text_classification', 'processing',
                              2, 4, 1, ?, 'upgrade-test-worker', ?, 'ai_provider_unavailable',
                              ?, ?, NULL, 3, ?, NULL, 'provider_retryable')
                    """)) {
                statement.setObject(1, jobId);
                statement.setObject(2, captureId);
                statement.setObject(3, userId);
                statement.setObject(4, availableAt);
                statement.setObject(5, leaseExpiresAt);
                statement.setObject(6, createdAt);
                statement.setObject(7, createdAt.plusMinutes(3));
                statement.setObject(8, createdAt.plusMinutes(1));
                assertEquals(1, statement.executeUpdate());
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO event_publication (
                        id, publication_date, listener_id, serialized_event, event_type,
                        completion_date, status, last_resubmission_date, completion_attempts
                        ) VALUES (?, ?, 'capture-text-job-redis-publisher', ?,
                              'com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent',
                              NULL, 'PUBLISHED', NULL, 0)
                    """)) {
                statement.setObject(1, publicationId);
                statement.setObject(2, createdAt.plusMinutes(4));
                statement.setString(3, serializedEvent);
                assertEquals(1, statement.executeUpdate());
            }
        }

        return new SeedData(userId, captureId, jobId, publicationId, eventId, createdAt, availableAt,
                leaseExpiresAt, serializedEvent);
    }

    private void assertSeededJobWasPreservedAndBackfilled(SeedData seed) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                    SELECT capture_id, user_id, status, attempt_count, max_attempts,
                           retry_generation, available_at, lease_owner, lease_expires_at,
                           last_error_code, dispatch_generation, last_dispatched_at,
                           last_failure_class, origin_request_id
                    FROM processing_jobs
                    WHERE id = ?
                    """)) {
            statement.setObject(1, seed.jobId());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(seed.captureId(), result.getObject("capture_id", UUID.class));
                assertEquals(seed.userId(), result.getObject("user_id", UUID.class));
                assertEquals("processing", result.getString("status"));
                assertEquals(2, result.getInt("attempt_count"));
                assertEquals(4, result.getInt("max_attempts"));
                assertEquals(1, result.getInt("retry_generation"));
                assertEquals(seed.availableAt(), result.getObject("available_at", OffsetDateTime.class));
                assertEquals("upgrade-test-worker", result.getString("lease_owner"));
                assertEquals(seed.leaseExpiresAt(), result.getObject("lease_expires_at", OffsetDateTime.class));
                assertEquals("ai_provider_unavailable", result.getString("last_error_code"));
                assertEquals(3, result.getInt("dispatch_generation"));
                assertEquals(seed.createdAt().plusMinutes(1),
                        result.getObject("last_dispatched_at", OffsetDateTime.class));
                assertEquals("provider_retryable", result.getString("last_failure_class"));
                assertEquals(seed.jobId().toString(), result.getString("origin_request_id"));
                assertFalse(result.next());
            }
        }
    }

    private void assertSeededOutboxEventWasPreserved(SeedData seed) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                    SELECT listener_id, serialized_event, event_type, completion_date,
                           status, completion_attempts
                    FROM event_publication
                    WHERE id = ?
                    """)) {
            statement.setObject(1, seed.publicationId());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("capture-text-job-redis-publisher", result.getString("listener_id"));
                assertEquals(seed.serializedEvent(), result.getString("serialized_event"));
                assertEquals("com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent",
                        result.getString("event_type"));
                assertNull(result.getObject("completion_date"));
                assertEquals("PUBLISHED", result.getString("status"));
                assertEquals(0, result.getInt("completion_attempts"));
            }
        }

        TextCaptureQueuedEvent legacyEvent = new ObjectMapper().readValue(
                seed.serializedEvent(),
                TextCaptureQueuedEvent.class
        );
        assertEquals(seed.eventId(), legacyEvent.eventId());
        assertEquals(seed.jobId(), legacyEvent.jobId());
        assertEquals(seed.captureId(), legacyEvent.captureId());
        assertEquals(3, legacyEvent.dispatchGeneration());
        assertEquals(seed.eventId().toString(), legacyEvent.originRequestId());
    }

    private void assertOriginRequestIdSchemaAndDefault(SeedData seed) throws SQLException {
        UUID defaultCaptureId = UUID.randomUUID();
        UUID defaultJobId = UUID.randomUUID();
        try (Connection connection = connection()) {
            insertCapture(connection, defaultCaptureId, seed.userId(), "Default correlation text",
                    seed.createdAt().plusDays(1));
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO processing_jobs (id, capture_id, user_id, job_type, status)
                    VALUES (?, ?, ?, 'text_classification', 'queued')
                    """)) {
                statement.setObject(1, defaultJobId);
                statement.setObject(2, defaultCaptureId);
                statement.setObject(3, seed.userId());
                assertEquals(1, statement.executeUpdate());
            }

            String generatedOrigin;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT origin_request_id FROM processing_jobs WHERE id = ?")) {
                statement.setObject(1, defaultJobId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    generatedOrigin = result.getString(1);
                }
            }
            assertNotNull(generatedOrigin);
            assertEquals(generatedOrigin, UUID.fromString(generatedOrigin).toString());

            SQLException invalidOrigin = assertThrows(SQLException.class, () -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE processing_jobs SET origin_request_id = 'invalid request id' WHERE id = ?")) {
                    statement.setObject(1, defaultJobId);
                    statement.executeUpdate();
                }
            });
            assertEquals("23514", invalidOrigin.getSQLState());
        }

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                    SELECT is_nullable, column_default
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'processing_jobs'
                      AND column_name = 'origin_request_id'
                    """);
                ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals("NO", result.getString("is_nullable"));
            assertTrue(result.getString("column_default").contains("gen_random_uuid"));
        }
    }

    private void assertObservabilityAndAdminIndexesExist() throws SQLException {
        Map<String, String> indexes = new HashMap<>();
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                    SELECT indexname, indexdef
                    FROM pg_indexes
                    WHERE schemaname = current_schema()
                      AND indexname IN (
                          'idx_event_publication_capture_incomplete',
                          'idx_processing_jobs_admin_created',
                          'idx_processing_jobs_admin_status_created'
                      )
                    """);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                indexes.put(result.getString("indexname"), result.getString("indexdef"));
            }
        }

        assertEquals(3, indexes.size());
        String outboxIndex = indexes.get("idx_event_publication_capture_incomplete");
        assertNotNull(outboxIndex);
        assertTrue(outboxIndex.contains("event_type, listener_id, publication_date"));
        assertTrue(outboxIndex.contains("completion_date IS NULL"));
        assertTrue(indexes.get("idx_processing_jobs_admin_created")
                .contains("created_at DESC, id DESC"));
        assertTrue(indexes.get("idx_processing_jobs_admin_status_created")
                .contains("status, created_at DESC, id DESC"));
    }

    private void assertPrivacyRetentionSchemaExists() throws SQLException {
        assertTrue(columnExists("audit_events", "deduplication_bucket"));
        Map<String, String> indexes = new HashMap<>();
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                    SELECT indexname, indexdef
                    FROM pg_indexes
                    WHERE schemaname = current_schema()
                      AND indexname IN (
                          'uq_audit_events_read_bucket',
                          'idx_event_publication_completed_cleanup',
                          'idx_processing_jobs_completed_cleanup'
                      )
                    """);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) indexes.put(result.getString(1), result.getString(2));
        }
        assertEquals(3, indexes.size());
        assertTrue(indexes.get("idx_event_publication_completed_cleanup").contains("completion_date IS NOT NULL"));
        String completedJobCleanupIndex = indexes.get("idx_processing_jobs_completed_cleanup")
                .replace("::character varying", "")
                .replace("::text", "")
                .replace("(", "")
                .replace(")", "")
                .replaceAll("\\s+", " ");
        assertTrue(completedJobCleanupIndex.contains("WHERE status = 'completed'"));
    }

    private void insertUser(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO users (id, display_name)
                VALUES (?, 'Migration upgrade user')
                """)) {
            statement.setObject(1, userId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void insertCapture(Connection connection, UUID captureId, UUID userId,
            String originalText, OffsetDateTime createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO captures (
                    id, user_id, original_text, processing_status, failure_code,
                    created_at, updated_at
                ) VALUES (?, ?, ?, 'processing', NULL, ?, ?)
                """)) {
            statement.setObject(1, captureId);
            statement.setObject(2, userId);
            statement.setString(3, originalText);
            statement.setObject(4, createdAt);
            statement.setObject(5, createdAt);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = ?
                          AND column_name = ?
                    )
                    """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record SeedData(
            UUID userId,
            UUID captureId,
            UUID jobId,
            UUID publicationId,
            UUID eventId,
            OffsetDateTime createdAt,
            OffsetDateTime availableAt,
            OffsetDateTime leaseExpiresAt,
            String serializedEvent) {
    }
}

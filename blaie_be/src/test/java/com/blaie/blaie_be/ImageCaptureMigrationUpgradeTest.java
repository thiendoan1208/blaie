package com.blaie.blaie_be;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ImageCaptureMigrationUpgradeTest {
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    ).withDatabaseName("image_capture_upgrade")
            .withUsername("capture_test")
            .withPassword("capture_test");

    @Test
    void upgradesV18WithoutLosingLegacyTextJobOrOutboxState() throws Exception {
        MigrateResult v18 = flywayAt(MigrationVersion.fromVersion("18")).migrate();
        assertEquals("18", v18.targetSchemaVersion);

        Seed seed = seedLegacyWorkflow();

        MigrateResult latest = flywayAt(null).migrate();
        assertEquals("19", latest.targetSchemaVersion);
        assertImageSchema();
        assertLegacyWorkflowPreserved(seed);
        latestFlyway().validate();
    }

    private void assertImageSchema() throws SQLException {
        assertTrue(columnExists("captures", "input_type"));
        assertTrue(columnNullable("captures", "original_text"));
        assertTrue(tableExists("capture_assets"));
        assertTrue(tableExists("storage_deletion_jobs"));

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT check_clause
                        FROM information_schema.check_constraints
                        WHERE constraint_name = 'processing_jobs_type_check'
                        """);
                ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertTrue(result.getString(1).contains("image_analysis"));
            assertFalse(result.next());
        }
    }

    private void assertLegacyWorkflowPreserved(Seed seed) throws SQLException {
        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT input_type, original_text
                    FROM captures
                    WHERE id = ?
                    """)) {
                statement.setObject(1, seed.captureId());
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("text", result.getString("input_type"));
                    assertEquals("Legacy text capture", result.getString("original_text"));
                    assertFalse(result.next());
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT job_type, status
                    FROM processing_jobs
                    WHERE id = ?
                    """)) {
                statement.setObject(1, seed.jobId());
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("text_classification", result.getString("job_type"));
                    assertEquals("queued", result.getString("status"));
                    assertFalse(result.next());
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT event_type, listener_id, serialized_event, completion_date
                    FROM event_publication
                    WHERE id = ?
                    """)) {
                statement.setObject(1, seed.publicationId());
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(
                            "com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent",
                            result.getString("event_type")
                    );
                    assertEquals("capture-text-job-redis-publisher", result.getString("listener_id"));
                    assertEquals(seed.serializedEvent(), result.getString("serialized_event"));
                    assertEquals(null, result.getObject("completion_date"));
                    assertFalse(result.next());
                }
            }
        }
    }

    private Seed seedLegacyWorkflow() throws SQLException {
        UUID userId = UUID.randomUUID();
        UUID captureId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.of(2026, 7, 28, 20, 0, 0, 0, ZoneOffset.UTC);
        String serializedEvent = """
                {"eventId":"%s","jobId":"%s","captureId":"%s","dispatchGeneration":1,"originRequestId":"%s"}
                """.formatted(eventId, jobId, captureId, eventId).strip();

        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users (id, display_name) VALUES (?, 'Image migration user')"
            )) {
                statement.setObject(1, userId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO captures (
                        id, user_id, original_text, processing_status, created_at, updated_at
                    ) VALUES (?, ?, 'Legacy text capture', 'processing', ?, ?)
                    """)) {
                statement.setObject(1, captureId);
                statement.setObject(2, userId);
                statement.setObject(3, now);
                statement.setObject(4, now);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO processing_jobs (
                        id, capture_id, user_id, job_type, status, attempt_count,
                        max_attempts, retry_generation, dispatch_generation, available_at,
                        last_dispatched_at, next_dispatch_at, origin_request_id,
                        created_at, updated_at
                    ) VALUES (
                        ?, ?, ?, 'text_classification', 'queued', 0,
                        4, 0, 1, ?, ?, ?, ?, ?, ?
                    )
                    """)) {
                statement.setObject(1, jobId);
                statement.setObject(2, captureId);
                statement.setObject(3, userId);
                statement.setObject(4, now);
                statement.setObject(5, now);
                statement.setObject(6, now.plusMinutes(1));
                statement.setString(7, eventId.toString());
                statement.setObject(8, now);
                statement.setObject(9, now);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO event_publication (
                        id, publication_date, listener_id, serialized_event, event_type,
                        completion_date, status, last_resubmission_date, completion_attempts
                    ) VALUES (
                        ?, ?, 'capture-text-job-redis-publisher', ?,
                        'com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent',
                        NULL, 'PUBLISHED', NULL, 0
                    )
                    """)) {
                statement.setObject(1, publicationId);
                statement.setObject(2, now);
                statement.setString(3, serializedEvent);
                statement.executeUpdate();
            }
        }
        return new Seed(captureId, jobId, publicationId, serializedEvent);
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT EXISTS (
                            SELECT 1 FROM information_schema.tables
                            WHERE table_schema = 'public' AND table_name = ?
                        )
                        """)) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        )
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private boolean columnNullable(String tableName, String columnName) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT is_nullable = 'YES'
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
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

    private Flyway latestFlyway() {
        return flywayAt(null);
    }

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }

    private record Seed(
            UUID captureId,
            UUID jobId,
            UUID publicationId,
            String serializedEvent
    ) {
    }
}

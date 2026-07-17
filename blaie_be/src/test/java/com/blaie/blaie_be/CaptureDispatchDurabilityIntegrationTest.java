package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.application.CaptureJobProcessor;
import com.blaie.blaie_be.capture.application.port.CaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import com.blaie.blaie_be.capture.infrastructure.async.CaptureProcessingProperties;
import com.blaie.blaie_be.capture.infrastructure.async.RedisCaptureMessageFinalizer;
import com.blaie.blaie_be.capture.infrastructure.persistence.ProcessingJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import({
        TestcontainersConfiguration.class,
        CaptureDispatchDurabilityIntegrationTest.ClassifierConfiguration.class
})
@SpringBootTest(properties = {
        "blaie.capture.processing.accept-async-enabled=true",
        "blaie.capture.processing.publisher-enabled=true",
        "blaie.capture.processing.worker-enabled=false",
        "blaie.capture.processing.recovery-enabled=false",
        "blaie.auth.access-token-secret=dispatch-test-access-secret-at-least-32-bytes",
        "blaie.email.provider=log",
        "blaie.email.from=Blaie <no-reply@test.local>",
        "blaie.email.web-base-url=http://localhost:3000",
        "blaie.email.api-base-url=http://localhost:8080/api/v1",
        "blaie.email.verification-ttl=24h",
        "blaie.google.oauth.client-id=test-google-client-id",
        "blaie.google.oauth.client-secret=test-google-client-secret",
        "blaie.google.oauth.redirect-uri=http://localhost:8080/api/v1/auth/google/callback",
        "blaie.google.oauth.web-base-url=http://localhost:3000"
})
class CaptureDispatchDurabilityIntegrationTest {
    private static final String TEXT = "Send the durable dispatch proposal";

    @Autowired
    private CaptureWorkflowStorePort workflowStore;

    @Autowired
    private ProcessingJobStorePort jobStore;

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private CaptureJobProcessor processor;

    @Autowired
    private CaptureProcessingProperties properties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanState() {
        redisTemplate.delete(properties.streamKey());
        jdbcTemplate.execute("delete from event_publication");
        jdbcTemplate.execute("delete from capture_items");
        jdbcTemplate.execute("delete from capture_idempotency_keys");
        jdbcTemplate.execute("delete from processing_jobs");
        jdbcTemplate.execute("delete from captures");
        jdbcTemplate.execute("delete from auth_action_tokens");
        jdbcTemplate.execute("delete from refresh_tokens");
        jdbcTemplate.execute("delete from auth_identities");
        jdbcTemplate.execute("delete from users");
        ClassifierConfiguration.ATTEMPTS.set(0);
    }

    @Test
    void lostRedisMessageIsRedispatchedAndDuplicateDeliveryOnlyProcessesOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, display_name) values (?, ?)",
                userId,
                "Dispatch Test User"
        );
        Instant now = Instant.now();
        CaptureResult capture = workflowStore.startTextCapture(
                userId,
                TEXT,
                UUID.randomUUID(),
                "0".repeat(64),
                now,
                now.plus(Duration.ofHours(24)),
                properties.maxAttempts()
        );
        UUID jobId = jdbcTemplate.queryForObject(
                "select id from processing_jobs where capture_id = ?",
                UUID.class,
                capture.id()
        );

        await(() -> streamRecords().size() == 1 && completedPublications() == 1);
        assertThat(redisTemplate.delete(properties.streamKey())).isTrue();
        jdbcTemplate.update(
                """
                update processing_jobs
                   set next_dispatch_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                 where id = ?
                """,
                jobId
        );

        assertThat(jobStore.redispatchStaleQueued(Instant.now(), 10)).isEqualTo(1);
        await(() -> streamRecords().size() == 1 && completedPublications() == 2);

        MapRecord<String, String, String> redispatched = streamRecords().getFirst();
        assertThat(redispatched.getValue().get("dispatchGeneration")).isEqualTo("2");
        redisTemplate.opsForStream().add(
                StreamRecords.<String, String, String>mapBacked(Map.of(
                        "eventId", UUID.randomUUID().toString(),
                        "jobId", jobId.toString(),
                        "captureId", capture.id().toString(),
                        "dispatchGeneration", "2"
                )).withStreamKey(properties.streamKey())
        );

        List<MapRecord<String, String, String>> deliveries = streamRecords();
        assertThat(deliveries).hasSize(2);
        for (int index = 0; index < deliveries.size(); index++) {
            MapRecord<String, String, String> delivery = deliveries.get(index);
            assertThat(processor.process(
                    UUID.fromString(delivery.getValue().get("jobId")),
                    Integer.parseInt(delivery.getValue().get("dispatchGeneration")),
                    "integration-worker-" + index
            )).isTrue();
        }

        assertThat(ClassifierConfiguration.ATTEMPTS).hasValue(1);
        assertThat(jdbcTemplate.queryForObject(
                "select status from processing_jobs where id = ?",
                String.class,
                jobId
        )).isEqualTo("completed");
        assertThat(jdbcTemplate.queryForObject(
                "select attempt_count from processing_jobs where id = ?",
                Integer.class,
                jobId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from capture_items where capture_id = ?",
                Integer.class,
                capture.id()
        )).isEqualTo(1);
    }

    @Test
    void v11BinaryCanWriteJobsAfterV12AndReconcilerUpgradesLegacyDispatchMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID captureId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, display_name) values (?, ?)",
                userId,
                "Rolling Deploy Test User"
        );
        jdbcTemplate.update(
                """
                insert into captures (id, user_id, original_text, processing_status)
                values (?, ?, ?, 'processing')
                """,
                captureId,
                userId,
                "Created by a V11 binary after V12 migration"
        );

        // V11 omits all V12 dispatch columns. Their database defaults must remain writable.
        jdbcTemplate.update(
                """
                insert into processing_jobs (
                    id, capture_id, user_id, job_type, status,
                    attempt_count, max_attempts, retry_generation, available_at
                ) values (?, ?, ?, 'text_classification', 'queued', 0, 4, 0, CURRENT_TIMESTAMP)
                """,
                jobId,
                captureId,
                userId
        );
        assertDispatchMetadata(jobId, 0, false, false);
        assertThat(jdbcTemplate.queryForObject(
                "select last_failure_class is null from processing_jobs where id = ?",
                Boolean.class,
                jobId
        )).isTrue();

        // Simulate V11 claim -> retry_wait -> dispatch. V11 never writes V12 columns.
        jdbcTemplate.update(
                """
                update processing_jobs
                   set status = 'processing', attempt_count = 1,
                       lease_owner = 'v11-worker',
                       lease_expires_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                 where id = ?
                """,
                jobId
        );
        jdbcTemplate.update(
                """
                update processing_jobs
                   set status = 'retry_wait', available_at = CURRENT_TIMESTAMP,
                       lease_owner = NULL, lease_expires_at = NULL,
                       last_error_code = 'ai_provider_unavailable'
                 where id = ?
                """,
                jobId
        );
        jdbcTemplate.update(
                "update processing_jobs set status = 'queued' where id = ?",
                jobId
        );

        assertThat(jobStore.redispatchStaleQueued(Instant.now(), 10)).isEqualTo(1);
        assertDispatchMetadata(jobId, 1, true, true);

        // A V11 worker can claim and finish a durable V12 dispatch without touching its metadata.
        jdbcTemplate.update(
                """
                update processing_jobs
                   set status = 'processing', attempt_count = 2,
                       lease_owner = 'v11-worker',
                       lease_expires_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                 where id = ?
                """,
                jobId
        );
        jdbcTemplate.update(
                """
                update processing_jobs
                   set status = 'dead', lease_owner = NULL, lease_expires_at = NULL,
                       completed_at = CURRENT_TIMESTAMP,
                       last_error_code = 'ai_provider_unavailable'
                 where id = ?
                """,
                jobId
        );

        // A pre-V13 worker cannot write last_failure_class. The new reader must
        // still derive the manual-retry policy from its known safe error code.
        assertThat(jdbcTemplate.queryForObject(
                "select last_failure_class is null from processing_jobs where id = ?",
                Boolean.class,
                jobId
        )).isTrue();
        assertThat(jobRepository.findById(jobId).orElseThrow().manualRetryAllowed()).isTrue();

        // New binaries clear next_dispatch_at in terminal states. A V11 manual restart must
        // still be accepted, and the reconciler must repair its missing dispatch schedule.
        jdbcTemplate.update(
                "update processing_jobs set next_dispatch_at = NULL where id = ?",
                jobId
        );
        jdbcTemplate.update(
                """
                update processing_jobs
                   set status = 'queued', attempt_count = 0, retry_generation = 1,
                       available_at = CURRENT_TIMESTAMP, completed_at = NULL,
                       last_error_code = NULL
                 where id = ?
                """,
                jobId
        );
        assertThat(jobRepository.findById(jobId).orElseThrow().manualRetryAllowed()).isFalse();

        assertThat(jobStore.redispatchStaleQueued(Instant.now(), 10)).isEqualTo(1);
        assertDispatchMetadata(jobId, 2, true, true);
        await(() -> completedPublications() == 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisSevenFinalizesAConsumedRecordAtomically() {
        StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
        var record = StreamRecords.<String, String, String>mapBacked(Map.of(
                "jobId", UUID.randomUUID().toString(),
                "dispatchGeneration", "1"
        )).withStreamKey(properties.streamKey());
        var recordId = streams.add(record);
        assertThat(recordId).isNotNull();
        streams.createGroup(properties.streamKey(), ReadOffset.from("0-0"), properties.consumerGroup());

        List<MapRecord<String, String, String>> delivered = streams.read(
                Consumer.from(properties.consumerGroup(), "finalizer-integration-test"),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(properties.streamKey(), ReadOffset.lastConsumed())
        );
        assertThat(delivered).hasSize(1);
        assertThat(streams.pending(properties.streamKey(), properties.consumerGroup())
                .getTotalPendingMessages()).isEqualTo(1);

        new RedisCaptureMessageFinalizer(redisTemplate, properties).acknowledgeAndDelete(recordId);

        assertThat(streams.size(properties.streamKey())).isZero();
        assertThat(streams.pending(properties.streamKey(), properties.consumerGroup())
                .getTotalPendingMessages()).isZero();
    }

    private void assertDispatchMetadata(
            UUID jobId,
            int generation,
            boolean hasLastDispatchedAt,
            boolean hasNextDispatchAt
    ) {
        Map<String, Object> metadata = jdbcTemplate.queryForMap(
                """
                select dispatch_generation, last_dispatched_at, next_dispatch_at
                  from processing_jobs
                 where id = ?
                """,
                jobId
        );
        assertThat(metadata.get("dispatch_generation")).isEqualTo(generation);
        assertThat(metadata.get("last_dispatched_at") != null).isEqualTo(hasLastDispatchedAt);
        assertThat(metadata.get("next_dispatch_at") != null).isEqualTo(hasNextDispatchAt);
    }

    private List<MapRecord<String, String, String>> streamRecords() {
        StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
        List<MapRecord<String, String, String>> records = streams.range(
                properties.streamKey(),
                Range.unbounded()
        );
        return records == null ? List.of() : records;
    }

    private int completedPublications() {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                  from event_publication
                 where listener_id = 'capture-text-job-redis-publisher'
                   and completion_date is not null
                """,
                Integer.class
        );
    }

    private void await(CheckedCondition condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition was not met before the timeout");
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean matches() throws Exception;
    }

    @TestConfiguration
    static class ClassifierConfiguration {
        private static final AtomicInteger ATTEMPTS = new AtomicInteger();

        @Bean
        @Primary
        TextClassifierPort durableDispatchClassifier() {
            return text -> {
                ATTEMPTS.incrementAndGet();
                return new CaptureAnalysis(
                        List.of(new ClassifiedTextItem(text, CaptureCategory.TASK)),
                        "test",
                        "durability-model",
                        "v1"
                );
            };
        }
    }
}

package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.infrastructure.ai.AiProviderConcurrencyProperties;
import com.blaie.blaie_be.capture.infrastructure.async.CaptureProcessingProperties;
import com.blaie.blaie_be.capture.infrastructure.observability.CaptureOperationalMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "blaie.capture.observability.collector-enabled=true",
        "blaie.capture.observability.snapshot-interval=1h",
        "blaie.capture.processing.accept-async-enabled=false",
        "blaie.capture.processing.publisher-enabled=false",
        "blaie.capture.processing.worker-enabled=false",
        "blaie.capture.processing.recovery-enabled=false",
        "blaie.ai.concurrency.key-prefix=test:metrics:ai",
        "blaie.ai.concurrency.provider-limits.deepseek=2",
        "blaie.capture.processing.stream-key=test:metrics:capture-stream",
        "blaie.capture.processing.consumer-group=test-metrics-workers",
        "blaie.auth.access-token-secret=metrics-test-access-secret-at-least-32-bytes",
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
class CaptureOperationalMetricsIntegrationTest {
    @Autowired
    private CaptureOperationalMetricsCollector collector;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CaptureProcessingProperties processingProperties;

    @Autowired
    private AiProviderConcurrencyProperties concurrencyProperties;

    @BeforeEach
    void cleanState() {
        redisTemplate.delete(processingProperties.streamKey());
        redisTemplate.delete(concurrencyProperties.keyFor("deepseek"));
        jdbcTemplate.execute("delete from event_publication");
        jdbcTemplate.execute("delete from capture_items");
        jdbcTemplate.execute("delete from capture_idempotency_keys");
        jdbcTemplate.execute("delete from processing_jobs");
        jdbcTemplate.execute("delete from captures");
        jdbcTemplate.execute("delete from auth_action_tokens");
        jdbcTemplate.execute("delete from refresh_tokens");
        jdbcTemplate.execute("delete from auth_identities");
        jdbcTemplate.execute("delete from users");
    }

    @Test
    void snapshotsPostgresOutboxRedisPendingAndDistributedProviderUsage() {
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("insert into users (id, display_name) values (?, ?)", userId, "Metrics User");
        insertJob(userId, "queued", now.minusSeconds(40), null);
        insertJob(userId, "retry_wait", now.plusSeconds(30), null);
        insertJob(userId, "processing", now, now.plusSeconds(60));
        insertJob(userId, "processing", now, now.minusSeconds(5));
        insertOutbox(now.minusSeconds(25), "capture-text-job-redis-publisher", null);
        insertOutbox(now.minusSeconds(10), "capture-text-job-redis-publisher", null);
        insertOutbox(now.minusSeconds(60), "another-listener", null);
        insertOutbox(now.minusSeconds(60), "capture-text-job-redis-publisher", now.minusSeconds(1));
        seedRedisPendingAndProviderUsage();

        collector.refresh();

        assertGauge("capture.queue.depth", "state", "queued", 1);
        assertGauge("capture.queue.depth", "state", "retry_wait", 1);
        assertGauge("capture.queue.depth", "state", "processing", 2);
        assertGauge("capture.active.leases", 1);
        assertGauge("capture.outbox.backlog", 2);
        assertGauge("capture.redis.stream.pending", 1);
        assertGauge("capture.redis.stream.length", 2);
        assertGauge("capture.provider.concurrency.usage", "provider", "deepseek", 1);
        assertGauge("capture.provider.concurrency.limit", "provider", "deepseek", 2);
        assertGauge("capture.observability.source.up", "source", "db", 1);
        assertGauge("capture.observability.source.up", "source", "redis", 1);
        assertThat(meterRegistry.get("capture.oldest.queued.age")
                .timeGauge().value(TimeUnit.SECONDS)).isBetween(35.0, 120.0);
        assertThat(meterRegistry.get("capture.outbox.oldest.age")
                .timeGauge().value(TimeUnit.SECONDS)).isBetween(20.0, 120.0);
    }

    private void insertJob(UUID userId, String status, Instant availableAt, Instant leaseExpiresAt) {
        UUID captureId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into captures (id, user_id, original_text, processing_status) values (?, ?, ?, 'processing')",
                captureId,
                userId,
                status + " metrics capture"
        );
        boolean processing = "processing".equals(status);
        boolean retryWait = "retry_wait".equals(status);
        jdbcTemplate.update("""
                insert into processing_jobs (
                    id, capture_id, user_id, job_type, status,
                    attempt_count, max_attempts, retry_generation, available_at,
                    lease_owner, lease_expires_at, last_error_code, last_failure_class,
                    created_at, updated_at, dispatch_generation, last_dispatched_at,
                    next_dispatch_at, origin_request_id
                ) values (?, ?, ?, 'text_classification', ?, ?, 4, 0, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                """,
                jobId,
                captureId,
                userId,
                status,
                processing ? 1 : 0,
                java.sql.Timestamp.from(availableAt),
                processing ? "metrics-worker" : null,
                leaseExpiresAt == null ? null : java.sql.Timestamp.from(leaseExpiresAt),
                retryWait ? "ai_provider_unavailable" : null,
                retryWait ? "provider_retryable" : null,
                java.sql.Timestamp.from(availableAt),
                java.sql.Timestamp.from(availableAt),
                java.sql.Timestamp.from(availableAt),
                "queued".equals(status) ? java.sql.Timestamp.from(Instant.now().plusSeconds(30)) : null,
                "metrics-" + jobId
        );
    }

    private void insertOutbox(Instant publicationDate, String listenerId, Instant completionDate) {
        jdbcTemplate.update("""
                insert into event_publication (
                    id, publication_date, listener_id, serialized_event, event_type,
                    completion_date, status, last_resubmission_date, completion_attempts
                ) values (?, ?, ?, '{}', ?, ?, 'PUBLISHED', null, 0)
                """,
                UUID.randomUUID(),
                java.sql.Timestamp.from(publicationDate),
                listenerId,
                TextCaptureQueuedEvent.class.getName(),
                completionDate == null ? null : java.sql.Timestamp.from(completionDate)
        );
    }

    private void seedRedisPendingAndProviderUsage() {
        var streams = redisTemplate.opsForStream();
        streams.add(processingProperties.streamKey(), Map.of("jobId", UUID.randomUUID().toString()));
        streams.createGroup(
                processingProperties.streamKey(),
                ReadOffset.from("0-0"),
                processingProperties.consumerGroup()
        );
        streams.read(
                Consumer.from(processingProperties.consumerGroup(), "metrics-consumer"),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(processingProperties.streamKey(), ReadOffset.lastConsumed())
        );
        streams.add(processingProperties.streamKey(), Map.of("jobId", UUID.randomUUID().toString()));
        redisTemplate.opsForZSet().add(
                concurrencyProperties.keyFor("deepseek"),
                "metrics-owner",
                Instant.now().plusSeconds(60).toEpochMilli()
        );
    }

    private void assertGauge(String name, double expected) {
        assertThat(meterRegistry.get(name).gauge().value()).isEqualTo(expected);
    }

    private void assertGauge(String name, String tag, String value, double expected) {
        assertThat(meterRegistry.get(name).tag(tag, value).gauge().value()).isEqualTo(expected);
    }
}

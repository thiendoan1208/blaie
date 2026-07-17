package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.CaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.infrastructure.async.CaptureProcessingProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "blaie.capture.processing.accept-async-enabled=true",
        "blaie.capture.processing.publisher-enabled=true",
        "blaie.capture.processing.worker-enabled=false",
        "blaie.capture.processing.recovery-enabled=false",
        "blaie.capture.processing.outbox-recovery-age=0s",
        "blaie.auth.access-token-secret=outbox-test-access-secret-at-least-32-bytes",
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
class CaptureOutboxFailureIntegrationTest {
    @Autowired
    private CaptureWorkflowStorePort workflowStore;

    @Autowired
    private CaptureProcessingProperties properties;

    @Autowired
    private IncompleteEventPublications eventPublications;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private StreamOperations<String, String, String> streams;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void cleanState() {
        streams = mock(StreamOperations.class);
        doReturn(streams).when(redisTemplate).opsForStream();
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
    void redisPublishFailureLeavesOutboxIncompleteUntilResubmissionSucceeds() throws Exception {
        when(streams.add(any()))
                .thenThrow(new RedisConnectionFailureException("simulated Redis outage"))
                .thenReturn(RecordId.of("1-0"));
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, display_name) values (?, ?)",
                userId,
                "Outbox Test User"
        );
        Instant now = Instant.now();

        workflowStore.startTextCapture(
                userId,
                "Publish this after Redis recovers",
                UUID.randomUUID(),
                "1".repeat(64),
                now,
                now.plus(Duration.ofHours(24)),
                properties.maxAttempts()
        );

        verify(streams, timeout(5_000)).add(any());
        await(() -> publicationCount(false) == 1);
        assertThat(jdbcTemplate.queryForObject(
                "select status from processing_jobs",
                String.class
        )).isEqualTo("queued");

        eventPublications.resubmitIncompletePublications(publication ->
                publication.getEvent() instanceof TextCaptureQueuedEvent
        );

        await(() -> publicationCount(true) == 1);
        verify(streams, timeout(5_000).atLeast(2)).add(any());
    }

    private int publicationCount(boolean completed) {
        String completionPredicate = completed
                ? "completion_date is not null"
                : "completion_date is null";
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                  from event_publication
                 where listener_id = 'capture-text-job-redis-publisher'
                   and %s
                """.formatted(completionPredicate),
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
}

package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.application.port.CaptureWorkflowStorePort;
import com.blaie.blaie_be.capture.application.result.CaptureResult;
import com.blaie.blaie_be.capture.infrastructure.persistence.ProcessingJobRepository;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.error.RateLimitedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "blaie.capture.processing.accept-async-enabled=false",
        "blaie.capture.processing.publisher-enabled=false",
        "blaie.capture.processing.worker-enabled=false",
        "blaie.capture.processing.recovery-enabled=false",
        "blaie.capture.processing.max-active-jobs-per-user=2",
        "blaie.capture.processing.max-active-jobs-total=3",
        "blaie.capture.processing.max-oldest-queued-age=1m",
        "blaie.capture.processing.admission-retry-after=17s",
        "blaie.auth.access-token-secret=admission-test-access-secret-at-least-32-bytes",
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
class CaptureAdmissionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Autowired
    private CaptureWorkflowStorePort workflowStore;

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanState() {
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
    void idempotentReplayBypassesAFullUserLimitWithoutCreatingAnotherWorkflow() {
        UUID userId = insertUser("Idempotency Admission User");
        UUID firstKey = UUID.randomUUID();
        CaptureResult first = start(userId, firstKey, "first");
        start(userId, UUID.randomUUID(), "second");

        CaptureResult replay = start(userId, firstKey, "first");
        assertThat(replay.id()).isEqualTo(first.id());

        AppException keyConflict = catchThrowableOfType(
                () -> start(userId, firstKey, "changed"),
                AppException.class
        );
        assertThat(keyConflict.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);

        assertAdmissionError(
                () -> start(userId, UUID.randomUUID(), "third"),
                ErrorCode.TOO_MANY_ACTIVE_JOBS
        );
        assertWorkflowCounts(2);
    }

    @Test
    void queuedProcessingAndRetryWaitAllCountTowardTheGlobalLimit() {
        List<CaptureResult> captures = List.of(
                start(insertUser("Queued User"), UUID.randomUUID(), "queued"),
                start(insertUser("Processing User"), UUID.randomUUID(), "processing"),
                start(insertUser("Retry User"), UUID.randomUUID(), "retry")
        );
        UUID processingJob = jobId(captures.get(1).id());
        UUID retryJob = jobId(captures.get(2).id());
        jdbcTemplate.update("""
                update processing_jobs
                   set status = 'processing', attempt_count = 1,
                       lease_owner = 'admission-test', lease_expires_at = ?,
                       next_dispatch_at = null
                 where id = ?
                """, timestamp(NOW.plusSeconds(30)), processingJob);
        jdbcTemplate.update("""
                update processing_jobs
                   set status = 'retry_wait', available_at = ?, next_dispatch_at = null
                 where id = ?
                """, timestamp(NOW.plusSeconds(30)), retryJob);

        assertThat(jobRepository.countActiveUpTo(10)).isEqualTo(3);
        assertAdmissionError(
                () -> start(insertUser("Rejected Global User"), UUID.randomUUID(), "overloaded"),
                ErrorCode.CAPTURE_PROCESSING_OVERLOADED
        );

        UUID queuedJob = jobId(captures.getFirst().id());
        jdbcTemplate.update("""
                update processing_jobs
                   set status = 'completed', completed_at = ?, next_dispatch_at = null
                 where id = ?
                """, timestamp(NOW), queuedJob);
        start(insertUser("Freed Global User"), UUID.randomUUID(), "accepted");
        assertThat(jobRepository.countActiveUpTo(10)).isEqualTo(3);
    }

    @Test
    void oldQueuedWorkTripsTheAgeCircuitBreakerBeforeTheCountLimit() {
        CaptureResult oldCapture = start(insertUser("Old Queue User"), UUID.randomUUID(), "old");
        jdbcTemplate.update(
                "update processing_jobs set available_at = ? where capture_id = ?",
                timestamp(NOW.minus(Duration.ofMinutes(2))),
                oldCapture.id()
        );

        assertAdmissionError(
                () -> start(insertUser("Age Rejected User"), UUID.randomUUID(), "new"),
                ErrorCode.CAPTURE_PROCESSING_OVERLOADED
        );
        assertWorkflowCounts(1);
    }

    @Test
    void rejectedManualRetryDoesNotMutateTheDeadWorkflowAndSucceedsAfterASlotFrees() {
        UUID userId = insertUser("Manual Retry Admission User");
        CaptureResult retryTarget = start(userId, UUID.randomUUID(), "retry-target");
        UUID targetJobId = jobId(retryTarget.id());
        makeProviderTerminalFailure(retryTarget.id(), targetJobId);

        CaptureResult firstActive = start(userId, UUID.randomUUID(), "active-one");
        start(userId, UUID.randomUUID(), "active-two");

        assertAdmissionError(
                () -> workflowStore.retryOwned(retryTarget.id(), userId, NOW),
                ErrorCode.TOO_MANY_ACTIVE_JOBS
        );
        assertThat(jobStatus(targetJobId)).isEqualTo("dead");
        assertThat(retryGeneration(targetJobId)).isZero();
        assertThat(captureStatus(retryTarget.id())).isEqualTo("failed");

        jdbcTemplate.update("""
                update processing_jobs
                   set status = 'completed', completed_at = ?, next_dispatch_at = null
                 where capture_id = ?
                """, timestamp(NOW), firstActive.id());

        CaptureResult restarted = workflowStore.retryOwned(retryTarget.id(), userId, NOW);
        assertThat(restarted.processingStatus().value()).isEqualTo("processing");
        assertThat(jobStatus(targetJobId)).isEqualTo("queued");
        assertThat(retryGeneration(targetJobId)).isEqualTo(1);
    }

    @Test
    void concurrentSubmissionsCannotRacePastThePerUserLimit() throws Exception {
        UUID userId = insertUser("Concurrent User");
        List<Attempt> attempts = runConcurrently(java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> (CheckedAttempt) () -> start(
                        userId,
                        UUID.randomUUID(),
                        "same-user-" + index
                ))
                .toList());

        assertThat(attempts).filteredOn(Attempt::accepted).hasSize(2);
        assertThat(attempts).filteredOn(attempt -> !attempt.accepted())
                .extracting(Attempt::errorCode)
                .containsOnly(ErrorCode.TOO_MANY_ACTIVE_JOBS);
        assertWorkflowCounts(2);
    }

    @Test
    void concurrentUsersCannotRacePastTheGlobalLimit() throws Exception {
        List<UUID> userIds = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> insertUser("Concurrent Global User " + index))
                .toList();
        List<Attempt> attempts = runConcurrently(java.util.stream.IntStream.range(0, userIds.size())
                .mapToObj(index -> (CheckedAttempt) () -> start(
                        userIds.get(index),
                        UUID.randomUUID(),
                        "global-" + index
                ))
                .toList());

        assertThat(attempts).filteredOn(Attempt::accepted).hasSize(3);
        assertThat(attempts).filteredOn(attempt -> !attempt.accepted())
                .extracting(Attempt::errorCode)
                .containsOnly(ErrorCode.CAPTURE_PROCESSING_OVERLOADED);
        assertWorkflowCounts(3);
    }

    private CaptureResult start(UUID userId, UUID idempotencyKey, String text) {
        return workflowStore.startTextCapture(
                userId,
                text,
                idempotencyKey,
                hash(text),
                NOW,
                NOW.plus(Duration.ofHours(24)),
                4
        );
    }

    private UUID insertUser(String displayName) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, display_name) values (?, ?)",
                userId,
                displayName
        );
        return userId;
    }

    private void makeProviderTerminalFailure(UUID captureId, UUID jobId) {
        jdbcTemplate.update("""
                update processing_jobs
                   set status = 'dead', completed_at = ?, next_dispatch_at = null,
                       last_error_code = 'ai_not_configured',
                       last_failure_class = 'provider_terminal'
                 where id = ?
                """, timestamp(NOW), jobId);
        jdbcTemplate.update("""
                update captures
                   set processing_status = 'failed', failure_code = 'ai_not_configured'
                 where id = ?
                """, captureId);
    }

    private List<Attempt> runConcurrently(List<CheckedAttempt> operations) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(operations.size());
        CountDownLatch ready = new CountDownLatch(operations.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Attempt>> futures = new ArrayList<>(operations.size());
        try {
            for (CheckedAttempt operation : operations) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        operation.run();
                        return new Attempt(true, null);
                    } catch (RateLimitedException exception) {
                        return new Attempt(false, exception.errorCode());
                    }
                }));
            }
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Attempt> results = new ArrayList<>(operations.size());
            for (Future<Attempt> future : futures) {
                results.add(future.get(10, java.util.concurrent.TimeUnit.SECONDS));
            }
            return List.copyOf(results);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void assertAdmissionError(Runnable operation, ErrorCode expectedCode) {
        RateLimitedException exception = catchThrowableOfType(operation::run, RateLimitedException.class);
        assertThat(exception.errorCode()).isEqualTo(expectedCode);
        assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(17));
    }

    private void assertWorkflowCounts(int expected) {
        assertThat(jdbcTemplate.queryForObject("select count(*) from captures", Integer.class)).isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject("select count(*) from processing_jobs", Integer.class)).isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject("select count(*) from capture_idempotency_keys", Integer.class))
                .isEqualTo(expected);
    }

    private UUID jobId(UUID captureId) {
        return jdbcTemplate.queryForObject(
                "select id from processing_jobs where capture_id = ?",
                UUID.class,
                captureId
        );
    }

    private String jobStatus(UUID jobId) {
        return jdbcTemplate.queryForObject(
                "select status from processing_jobs where id = ?",
                String.class,
                jobId
        );
    }

    private int retryGeneration(UUID jobId) {
        return jdbcTemplate.queryForObject(
                "select retry_generation from processing_jobs where id = ?",
                Integer.class,
                jobId
        );
    }

    private String captureStatus(UUID captureId) {
        return jdbcTemplate.queryForObject(
                "select processing_status from captures where id = ?",
                String.class,
                captureId
        );
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private java.sql.Timestamp timestamp(Instant instant) {
        return java.sql.Timestamp.from(instant);
    }

    @FunctionalInterface
    private interface CheckedAttempt {
        void run();
    }

    private record Attempt(boolean accepted, ErrorCode errorCode) {
    }
}

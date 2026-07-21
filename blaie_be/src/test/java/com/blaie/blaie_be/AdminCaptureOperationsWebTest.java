package com.blaie.blaie_be;

import com.blaie.blaie_be.auth.infrastructure.security.AuthTokenService;
import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.core.request.RequestContextFilter;
import com.blaie.blaie_be.core.security.AuthCookieNames;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "blaie.capture.processing.accept-async-enabled=true",
        "blaie.capture.processing.publisher-enabled=true",
        "blaie.capture.processing.worker-enabled=false",
        "blaie.capture.processing.recovery-enabled=false",
        "blaie.capture.processing.max-active-jobs-per-user=1",
        "blaie.capture.processing.max-active-jobs-total=100",
        "blaie.capture.processing.max-oldest-queued-age=1h",
        "blaie.capture.processing.admission-retry-after=17s",
        "blaie.capture.observability.collector-enabled=false",
        "blaie.ai.concurrency.enabled=false",
        "blaie.rate-limit.enabled=false",
        "blaie.auth.access-token-secret=admin-operations-access-secret-at-least-32-bytes",
        "blaie.auth.cookie-secure=false",
        "blaie.security.cors.allowed-origins=http://localhost:3000",
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
class AdminCaptureOperationsWebTest {
    private static final String PRIVATE_CAPTURE_TEXT = "private-user-text-must-never-leak";
    private static final String PRIVATE_OUTBOX_PAYLOAD = "private-serialized-event-must-never-leak";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestContextFilter requestContextFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private ProcessingJobStorePort processingJobStore;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanState() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestContextFilter)
                .apply(springSecurity())
                .build();
        jdbcTemplate.execute("delete from audit_events");
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
    void adminReadsAreProtectedPaginatedFilteredAndNeverLeakPrivatePayloads() throws Exception {
        UserAccess regular = user(false, "regular-operator");
        UserAccess admin = user(true, "admin-operator");
        UUID ownerId = user(false, "capture-owner").id();
        Instant base = Instant.now().minusSeconds(60);
        JobRecord dueQueued = insertJob(ownerId, "queued", base.plusSeconds(3), null, base.minusSeconds(1));
        insertJob(ownerId, "queued", base.plusSeconds(2), null, Instant.now().plusSeconds(300));
        JobRecord dead = insertJob(
                ownerId,
                "dead",
                base.plusSeconds(1),
                "provider_retryable",
                null
        );
        insertOutbox(false, TextCaptureQueuedEvent.class.getName(), "capture-text-job-redis-publisher", base);

        mockMvc.perform(get("/api/v1/admin/jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(regular.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(regular.token()))
                        .queryParam("limit", "abc"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MvcResult firstPage = mockMvc.perform(get("/api/v1/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.hasMore").value(true))
                .andExpect(jsonPath("$.meta.nextCursor").isNotEmpty())
                .andReturn();
        assertNoPrivateData(firstPage);

        String cursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .path("meta")
                .path("nextCursor")
                .asString();
        MvcResult secondPage = mockMvc.perform(get("/api/v1/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("limit", "2")
                        .queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.hasMore").value(false))
                .andReturn();
        assertNoPrivateData(secondPage);

        MvcResult stuckPage = mockMvc.perform(get("/api/v1/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("status", "queued")
                        .queryParam("stuck", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(dueQueued.jobId().toString()))
                .andReturn();
        assertNoPrivateData(stuckPage);

        MvcResult detail = mockMvc.perform(get("/api/v1/admin/jobs/{jobId}", dead.jobId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captureId").value(dead.captureId().toString()))
                .andExpect(jsonPath("$.data.status").value("dead"))
                .andExpect(jsonPath("$.data.lastFailureClass").value("provider_retryable"))
                .andExpect(jsonPath("$.data.manualRetryAllowed").value(true))
                .andReturn();
        assertNoPrivateData(detail);

        mockMvc.perform(get("/api/v1/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("status", "pending"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("limit", "abc"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformedJobIdsAreValidatedOnlyAfterAdminAuthorization() throws Exception {
        UserAccess regular = user(false, "malformed-job-regular");
        UserAccess admin = user(true, "malformed-job-admin");

        mockMvc.perform(get("/api/v1/admin/jobs/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(regular.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/jobs/not-a-uuid/requeue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(regular.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/jobs/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/admin/jobs/not-a-uuid/requeue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/admin/jobs/not-a-uuid/mark-dead")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requeueIsPolicyAwareAtomicAndSubjectToAdmission() throws Exception {
        UserAccess admin = user(true, "requeue-admin");
        UserAccess regular = user(false, "requeue-regular");
        UUID ownerId = user(false, "requeue-owner").id();
        Instant createdAt = Instant.now().minusSeconds(30);
        JobRecord retryable = insertJob(
                ownerId,
                "dead",
                createdAt,
                "provider_retryable",
                null
        );
        int outboxBefore = count("event_publication");

        mockMvc.perform(post("/api/v1/admin/jobs/{jobId}/requeue", retryable.jobId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(regular.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(jobStatus(retryable.jobId())).isEqualTo("dead");
        assertThat(count("event_publication")).isEqualTo(outboxBefore);

        mockMvc.perform(post("/api/v1/admin/jobs/{jobId}/requeue", retryable.jobId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("queued"))
                .andExpect(jsonPath("$.data.attemptCount").value(0))
                .andExpect(jsonPath("$.data.retryGeneration").value(1))
                .andExpect(jsonPath("$.data.lastErrorCode").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.manualRetryAllowed").value(false));

        assertThat(jobStatus(retryable.jobId())).isEqualTo("queued");
        assertThat(captureStatus(retryable.captureId())).isEqualTo("processing");
        assertThat(count("event_publication")).isEqualTo(outboxBefore + 1);

        mockMvc.perform(post("/api/v1/admin/jobs/{jobId}/requeue", retryable.jobId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROCESSING_JOB_REQUEUE_NOT_ALLOWED"));

        JobRecord contentTerminal = insertJob(
                ownerId,
                "dead",
                createdAt.minusSeconds(1),
                "content_terminal",
                null
        );
        int beforePolicyReject = count("event_publication");
        mockMvc.perform(post("/api/v1/admin/jobs/{jobId}/requeue", contentTerminal.jobId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROCESSING_JOB_REQUEUE_NOT_ALLOWED"));
        assertThat(jobStatus(contentTerminal.jobId())).isEqualTo("dead");
        assertThat(count("event_publication")).isEqualTo(beforePolicyReject);

        UUID capacityOwner = user(false, "capacity-owner").id();
        JobRecord capacityTarget = insertJob(
                capacityOwner,
                "dead",
                createdAt.minusSeconds(2),
                "system_retryable",
                null
        );
        insertJob(capacityOwner, "queued", createdAt.minusSeconds(3), null, Instant.now().plusSeconds(60));
        int beforeCapacityReject = count("event_publication");

        mockMvc.perform(post("/api/v1/admin/jobs/{jobId}/requeue", capacityTarget.jobId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "17"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ACTIVE_JOBS"));

        assertThat(jobStatus(capacityTarget.jobId())).isEqualTo("dead");
        assertThat(captureStatus(capacityTarget.captureId())).isEqualTo("failed");
        assertThat(count("event_publication")).isEqualTo(beforeCapacityReject);
    }

    @Test
    void markDeadIsCsrfProtectedAndFencesAStaleWorkerCommit() throws Exception {
        UserAccess admin = user(true, "mark-dead-admin");
        UUID ownerId = user(false, "mark-dead-owner").id();
        JobRecord processing = insertJob(
                ownerId,
                "processing",
                Instant.now().minusSeconds(20),
                null,
                null
        );

        mockMvc.perform(post("/api/v1/admin/jobs/{jobId}/mark-dead", processing.jobId())
                        .cookie(new MockCookie(AuthCookieNames.ACCESS_COOKIE_NAME, admin.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(jobStatus(processing.jobId())).isEqualTo("processing");

        mockMvc.perform(post("/api/v1/admin/jobs/{jobId}/mark-dead", processing.jobId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("dead"))
                .andExpect(jsonPath("$.data.lastErrorCode").value("operator_marked_dead"))
                .andExpect(jsonPath("$.data.lastFailureClass").value("system_retryable"))
                .andExpect(jsonPath("$.data.manualRetryAllowed").value(true));

        assertThat(jobStatus(processing.jobId())).isEqualTo("dead");
        assertThat(captureStatus(processing.captureId())).isEqualTo("failed");
        assertThat(jdbcTemplate.queryForObject(
                "select lease_owner is null and lease_expires_at is null from processing_jobs where id = ?",
                Boolean.class,
                processing.jobId()
        )).isTrue();

        boolean staleCommit = processingJobStore.complete(
                processing.jobId(),
                "admin-test-worker",
                1,
                0,
                new CaptureAnalysis(List.of(), "test", "model", "v1"),
                Instant.now()
        );
        assertThat(staleCommit).isFalse();
        assertThat(jobStatus(processing.jobId())).isEqualTo("dead");

        mockMvc.perform(post("/api/v1/admin/jobs/{jobId}/mark-dead", processing.jobId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROCESSING_JOB_MARK_DEAD_NOT_ALLOWED"));
    }

    @Test
    void concurrentRequeueHasExactlyOneWinnerAndOneOutboxPublication() throws Exception {
        UserAccess admin = user(true, "concurrent-requeue-admin");
        UUID ownerId = user(false, "concurrent-requeue-owner").id();
        JobRecord retryable = insertJob(
                ownerId,
                "dead",
                Instant.now().minusSeconds(30),
                "provider_retryable",
                null
        );
        int outboxBefore = count("event_publication");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> concurrentRequeueStatus(admin, retryable, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentRequeueStatus(admin, retryable, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(HttpStatus.ACCEPTED.value(), HttpStatus.CONFLICT.value());
        }

        assertThat(jobStatus(retryable.jobId())).isEqualTo("queued");
        assertThat(count("event_publication")).isEqualTo(outboxBefore + 1);
    }

    @Test
    void outboxSummaryFiltersExactCapturePublicationAndNeverReturnsSerializedEvent() throws Exception {
        UserAccess admin = user(true, "outbox-admin");
        Instant now = Instant.now();
        insertOutbox(
                false,
                TextCaptureQueuedEvent.class.getName(),
                "capture-text-job-redis-publisher",
                now.minusSeconds(120)
        );
        insertOutbox(
                false,
                TextCaptureQueuedEvent.class.getName(),
                "capture-text-job-redis-publisher",
                now.minusSeconds(30)
        );
        insertOutbox(
                true,
                TextCaptureQueuedEvent.class.getName(),
                "capture-text-job-redis-publisher",
                now.minusSeconds(300)
        );
        insertOutbox(false, "other.Event", "capture-text-job-redis-publisher", now.minusSeconds(400));
        insertOutbox(false, TextCaptureQueuedEvent.class.getName(), "other-listener", now.minusSeconds(500));

        MvcResult result = mockMvc.perform(get("/api/v1/admin/outbox/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backlogCount").value(2))
                .andExpect(jsonPath("$.data.oldestPublicationAt").isNotEmpty())
                .andExpect(jsonPath("$.data.oldestAgeSeconds").value(org.hamcrest.Matchers.greaterThanOrEqualTo(119)))
                .andExpect(jsonPath("$.data.maxCompletionAttempts").value(2))
                .andReturn();
        assertNoPrivateData(result);
    }

    @Test
    void auditEventsRequireAdminAndUseSafeSignedPagination() throws Exception {
        UserAccess regular = user(false, "audit-regular");
        UserAccess admin = user(true, "audit-admin");
        Instant base = Instant.now().minusSeconds(60);
        insertAudit("capture.delete", "capture", UUID.randomUUID().toString(), "success", base.plusSeconds(3));
        insertAudit("capture.read", "capture", UUID.randomUUID().toString(), "not_found", base.plusSeconds(2));
        insertAudit("inbox.list", "inbox", regular.id().toString(), "success", base.plusSeconds(1));

        MvcResult firstPage = mockMvc.perform(get("/api/v1/admin/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].action").value("capture.delete"))
                .andExpect(jsonPath("$.data[0].originalText").doesNotExist())
                .andExpect(jsonPath("$.meta.hasMore").value(true))
                .andExpect(jsonPath("$.meta.nextCursor").isNotEmpty())
                .andReturn();
        assertNoPrivateData(firstPage);

        String cursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .path("meta")
                .path("nextCursor")
                .asString();
        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("limit", "2")
                        .queryParam("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.hasMore").value(false));

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .queryParam("cursor", cursor + "tampered"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(regular.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private UserAccess user(boolean admin, String username) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, username, username_normalized, display_name, admin) values (?, ?, ?, ?, ?)",
                userId,
                username,
                username,
                "Admin Operations Test User",
                admin
        );
        jdbcTemplate.update("""
                insert into auth_identities (
                    id, user_id, provider, email_verified, password_hash
                ) values (?, ?, 'local', true, ?)
                """,
                UUID.randomUUID(),
                userId,
                "not-used-by-this-test"
        );
        return new UserAccess(userId, authTokenService.issueAccessToken(userId));
    }

    private int concurrentRequeueStatus(
            UserAccess admin,
            JobRecord job,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/v1/admin/jobs/{jobId}/requeue", job.jobId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private JobRecord insertJob(
            UUID userId,
            String status,
            Instant createdAt,
            String failureClass,
            Instant queuedNextDispatchAt
    ) {
        UUID captureId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        boolean terminal = "dead".equals(status) || "completed".equals(status);
        String captureStatus = "dead".equals(status) ? "failed"
                : "completed".equals(status) ? "completed" : "processing";
        String errorCode = "dead".equals(status)
                ? errorCodeFor(failureClass)
                : null;
        jdbcTemplate.update("""
                insert into captures (
                    id, user_id, original_text, processing_status, failure_code, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                captureId,
                userId,
                PRIVATE_CAPTURE_TEXT,
                captureStatus,
                errorCode,
                timestamp(createdAt),
                timestamp(createdAt)
        );

        int attemptCount = switch (status) {
            case "queued" -> 0;
            case "dead" -> 4;
            default -> 1;
        };
        Instant availableAt = "queued".equals(status) && queuedNextDispatchAt != null
                ? createdAt
                : createdAt;
        String leaseOwner = "processing".equals(status) ? "admin-test-worker" : null;
        Instant leaseExpiresAt = "processing".equals(status) ? Instant.now().plusSeconds(120) : null;
        Instant completedAt = terminal ? createdAt.plusSeconds(1) : null;
        Instant nextDispatchAt = "queued".equals(status)
                ? queuedNextDispatchAt
                : null;
        jdbcTemplate.update("""
                insert into processing_jobs (
                    id, capture_id, user_id, job_type, origin_request_id, status,
                    attempt_count, max_attempts, retry_generation, dispatch_generation,
                    available_at, lease_owner, lease_expires_at, last_error_code,
                    last_failure_class, last_dispatched_at, next_dispatch_at,
                    created_at, updated_at, completed_at
                ) values (?, ?, ?, 'text_classification', ?, ?, ?, 4, 0, 1,
                          ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                jobId,
                captureId,
                userId,
                "request-" + jobId,
                status,
                attemptCount,
                timestamp(availableAt),
                leaseOwner,
                timestamp(leaseExpiresAt),
                errorCode,
                failureClass,
                timestamp(createdAt),
                timestamp(nextDispatchAt),
                timestamp(createdAt),
                timestamp(createdAt),
                timestamp(completedAt)
        );
        return new JobRecord(jobId, captureId);
    }

    private void insertOutbox(
            boolean completed,
            String eventType,
            String listenerId,
            Instant publicationDate
    ) {
        jdbcTemplate.update("""
                insert into event_publication (
                    id, publication_date, listener_id, serialized_event, event_type,
                    completion_date, status, last_resubmission_date, completion_attempts
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                timestamp(publicationDate),
                listenerId,
                PRIVATE_OUTBOX_PAYLOAD,
                eventType,
                timestamp(completed ? publicationDate.plusSeconds(1) : null),
                completed ? "COMPLETED" : "FAILED",
                timestamp(completed ? null : publicationDate.plusSeconds(5)),
                completed ? 1 : 2
        );
    }

    private void insertAudit(
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            Instant occurredAt
    ) {
        jdbcTemplate.update("""
                insert into audit_events (
                    id, actor_id, actor_admin, action, resource_type, resource_id,
                    outcome, request_id, occurred_at, deduplication_bucket
                ) values (?, ?, true, ?, ?, ?, ?, ?, ?, null)
                """,
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                action,
                resourceType,
                resourceId,
                outcome,
                "audit-test-request",
                timestamp(occurredAt)
        );
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String errorCodeFor(String failureClass) {
        return switch (failureClass) {
            case "content_terminal" -> "sensitive_credential_detected";
            case "provider_terminal" -> "ai_provider_rejected";
            case "provider_retryable" -> "ai_provider_unavailable";
            default -> "unexpected_classification_error";
        };
    }

    private String jobStatus(UUID jobId) {
        return jdbcTemplate.queryForObject(
                "select status from processing_jobs where id = ?",
                String.class,
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

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void assertNoPrivateData(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();
        assertThat(response)
                .doesNotContain(PRIVATE_CAPTURE_TEXT)
                .doesNotContain(PRIVATE_OUTBOX_PAYLOAD)
                .doesNotContain("originalText")
                .doesNotContain("serializedEvent")
                .doesNotContain("errorStack")
                .doesNotContain("payload");

        JsonNode root = objectMapper.readTree(response);
        Assertions.assertThat(root).isNotNull();
    }

    private record UserAccess(UUID id, String token) {
    }

    private record JobRecord(UUID jobId, UUID captureId) {
    }
}

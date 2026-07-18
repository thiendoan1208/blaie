package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import com.blaie.blaie_be.core.request.RequestContextFilter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@Import({TestcontainersConfiguration.class, InboxWebFlowTest.ClassifierTestConfiguration.class})
@SpringBootTest(properties = {
        "blaie.ai.provider=stub",
        "blaie.capture.processing.poll-interval=100ms",
        "blaie.capture.processing.recovery-interval=100ms",
        "blaie.capture.processing.retry-delays[0]=100ms",
        "blaie.capture.processing.retry-delays[1]=100ms",
        "blaie.capture.processing.retry-delays[2]=100ms",
        "blaie.auth.access-token-secret=test-access-secret-that-is-at-least-32-bytes",
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
class InboxWebFlowTest {
    private static final String ACCESS_COOKIE = "blaie_at";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestContextFilter requestContextFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
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
        ClassifierTestConfiguration.ATTEMPTS.clear();
        ClassifierTestConfiguration.LAST_PROVIDER_TEXT = null;
    }

    @Test
    void verifiedUserCanCaptureAndOnlyOwnerCanReadTheItem() throws Exception {
        String ownerUsername = uniqueValue("inbox-owner");
        String ownerToken = registeredAndVerifiedAccessToken(ownerUsername);
        String idempotencyKey = UUID.randomUUID().toString();

        MvcResult capture = mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Send the proposal tomorrow\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.originalText").value("Send the proposal tomorrow"))
                .andExpect(jsonPath("$.data.processingStatus").value("processing"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andReturn();
        String captureId = capture.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Send the proposal tomorrow\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(captureId));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from processing_jobs where capture_id = ?",
                Integer.class,
                UUID.fromString(captureId)
        )).isEqualTo(1);

        mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Different request\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        awaitCaptureStatus(captureId, "completed");

        MvcResult completedCapture = mockMvc.perform(get("/api/v1/captures/{captureId}", captureId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingStatus").value("completed"))
                .andExpect(jsonPath("$.data.items[0].captureId").value(captureId))
                .andExpect(jsonPath("$.data.items[0].category").value("task"))
                .andReturn();
        String itemId = completedCapture.getResponse().getContentAsString()
                .replaceAll(".*\\\"items\\\":\\[\\{\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertThat(UUID.fromString(itemId)).isNotNull();

        mockMvc.perform(get("/api/v1/inbox")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(itemId))
                .andExpect(jsonPath("$.data[0].captureId").value(captureId))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
        UUID ownerId = jdbcTemplate.queryForObject("""
                select id from users where username_normalized = ?
                """, UUID.class, ownerUsername.toLowerCase());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_events
                where actor_id = ? and action = 'inbox.list'
                  and resource_id = ? and outcome = 'success'
                """, Integer.class, ownerId.toString(), ownerId.toString())).isEqualTo(1);

        String otherToken = registeredAndVerifiedAccessToken(uniqueValue("inbox-other"));
        mockMvc.perform(get("/api/v1/inbox/items/{itemId}", itemId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAPTURE_ITEM_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/captures/{captureId}", captureId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAPTURE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/captures/{captureId}/retry", captureId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAPTURE_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/captures/{captureId}", captureId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAPTURE_NOT_FOUND"));
        assertThat(countBy("captures", "id", UUID.fromString(captureId))).isEqualTo(1);
    }

    @Test
    void unverifiedUserCannotCaptureText() throws Exception {
        String username = uniqueValue("inbox-unverified");
        MvcResult registered = register(username);

        mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + cookieValue(registered, ACCESS_COOKIE))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Pay rent\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void cancelledCaptureCompletesWithoutCreatingAnInboxItem() throws Exception {
        String accessToken = registeredAndVerifiedAccessToken(uniqueValue("inbox-cancelled"));

        MvcResult capture = mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"CANCELLED_INPUT\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.processingStatus").value("processing"))
                .andReturn();
        String captureId = capture.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        awaitCaptureStatus(captureId, "completed");

        mockMvc.perform(get("/api/v1/captures/{captureId}", captureId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingStatus").value("completed"))
                .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(get("/api/v1/inbox")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void retryableProviderFailureIsAutomaticallyRetriedThroughTheQueue() throws Exception {
        String accessToken = registeredAndVerifiedAccessToken(uniqueValue("inbox-retry"));
        String text = "RETRY_ONCE_" + UUID.randomUUID();

        MvcResult capture = mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String captureId = capture.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        awaitCaptureStatus(captureId, "completed");
        assertThat(jdbcTemplate.queryForObject(
                "select attempt_count from processing_jobs where capture_id = ?",
                Integer.class,
                UUID.fromString(captureId)
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from capture_items where capture_id = ?",
                Integer.class,
                UUID.fromString(captureId)
        )).isEqualTo(1);
    }

    @Test
    void sensitiveCredentialIsRejectedBeforeAnyWorkflowStateIsPersisted() throws Exception {
        String accessToken = registeredAndVerifiedAccessToken(uniqueValue("secret-no-retry"));
        String text = "Store sk-abcdefghijklmnopqrstuvwxyz123456";

        mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CAPTURE_SENSITIVE_CONTENT"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from captures", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from processing_jobs", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from capture_idempotency_keys", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from event_publication", Integer.class)).isZero();
    }

    @Test
    void providerReceivesMaskedPiiAndInboxRestoresTheExactUserText() throws Exception {
        String accessToken = registeredAndVerifiedAccessToken(uniqueValue("pii-mask"));
        String original = "Email jane@example.com or call +1 (416) 555-0199";

        MvcResult capture = mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + original + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String captureId = capture.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        awaitCaptureStatus(captureId, "completed");

        assertThat(ClassifierTestConfiguration.LAST_PROVIDER_TEXT)
                .doesNotContain("jane@example.com", "+1 (416) 555-0199")
                .contains("__BLAIE_PII_EMAIL_", "__BLAIE_PII_PHONE_");
        mockMvc.perform(get("/api/v1/captures/{captureId}", captureId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalText").value(original))
                .andExpect(jsonPath("$.data.items[0].originalText").value(original));
    }

    @Test
    void ownerCanDeleteCaptureAndAllDerivedPersistentState() throws Exception {
        String accessToken = registeredAndVerifiedAccessToken(uniqueValue("capture-delete"));
        MvcResult created = mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Delete this reminder\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String captureId = created.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        awaitCaptureStatus(captureId, "completed");

        mockMvc.perform(delete("/api/v1/captures/{captureId}", captureId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        UUID id = UUID.fromString(captureId);
        assertThat(countBy("captures", "id", id)).isZero();
        assertThat(countBy("capture_items", "capture_id", id)).isZero();
        assertThat(countBy("processing_jobs", "capture_id", id)).isZero();
        assertThat(countBy("capture_idempotency_keys", "capture_id", id)).isZero();
        mockMvc.perform(get("/api/v1/captures/{captureId}", captureId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_events
                where action = 'capture.delete' and resource_id = ? and outcome = 'success'
                """, Integer.class, captureId)).isEqualTo(1);
    }

    @Test
    void ownerCanRetryProviderTerminalFailureAfterProviderIsCorrected() throws Exception {
        String accessToken = registeredAndVerifiedAccessToken(uniqueValue("provider-retry"));
        String text = "MANUAL_RETRY_ONCE_" + UUID.randomUUID();

        MvcResult capture = mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String captureId = capture.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        awaitCaptureStatus(captureId, "failed");

        mockMvc.perform(get("/api/v1/captures/{captureId}", captureId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingStatus").value("failed"))
                .andExpect(jsonPath("$.data.failureCode").value("ai_not_configured"))
                .andExpect(jsonPath("$.data.canRetry").value(true));

        mockMvc.perform(post("/api/v1/captures/{captureId}/retry", captureId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(captureId))
                .andExpect(jsonPath("$.data.processingStatus").value("processing"))
                .andExpect(jsonPath("$.data.canRetry").value(false));

        awaitCaptureStatus(captureId, "completed");
        mockMvc.perform(get("/api/v1/captures/{captureId}", captureId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingStatus").value("completed"))
                .andExpect(jsonPath("$.data.canRetry").value(false))
                .andExpect(jsonPath("$.data.items[0].category").value("task"));

        assertThat(jdbcTemplate.queryForObject(
                "select retry_generation from processing_jobs where capture_id = ?",
                Integer.class,
                UUID.fromString(captureId)
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from captures where id = ?",
                Integer.class,
                UUID.fromString(captureId)
        )).isEqualTo(1);
    }

    private String registeredAndVerifiedAccessToken(String username) throws Exception {
        MvcResult registered = register(username);
        jdbcTemplate.update("""
                update auth_identities
                   set email_verified = true
                  from users
                 where auth_identities.user_id = users.id
                   and users.username_normalized = ?
                """, username.toLowerCase());
        return cookieValue(registered, ACCESS_COOKIE);
    }

    private void awaitCaptureStatus(String captureId, String expectedStatus) throws InterruptedException {
        UUID id = UUID.fromString(captureId);
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            String status = jdbcTemplate.queryForObject(
                    "select processing_status from captures where id = ?",
                    String.class,
                    id
            );
            if (expectedStatus.equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Capture " + captureId + " did not reach status " + expectedStatus);
    }

    private MvcResult register(String username) throws Exception {
        return mockMvc.perform(withCsrf(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "displayName", "Inbox Test User",
                                "password", "Password1!"
                        )))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String token = cookieValue(csrfResult, CSRF_COOKIE);
        return requestBuilder.cookie(new MockCookie(CSRF_COOKIE, token)).header(CSRF_HEADER, token);
    }

    private String cookieValue(MvcResult result, String cookieName) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing cookie " + cookieName));
        int valueEnd = header.indexOf(';');
        return header.substring((cookieName + "=").length(), valueEnd < 0 ? header.length() : valueEnd);
    }

    private String uniqueValue(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private int countBy(String table, String column, UUID id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Integer.class,
                id
        );
    }

    private String json(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    @TestConfiguration
    static class ClassifierTestConfiguration {
        private static final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicInteger>
                ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();
        private static volatile String LAST_PROVIDER_TEXT;

        @Bean
        @Primary
        TextClassifierPort testTextClassifier() {
            return text -> {
                LAST_PROVIDER_TEXT = text;
                if (text.startsWith("RETRY_ONCE_")
                        && ATTEMPTS.computeIfAbsent(text, ignored -> new java.util.concurrent.atomic.AtomicInteger())
                        .incrementAndGet() == 1) {
                    throw new TextClassificationException(
                            "ai_provider_unavailable",
                            "simulated retryable provider failure",
                            TextClassificationFailureClass.PROVIDER_RETRYABLE
                    );
                }
                if (text.startsWith("MANUAL_RETRY_ONCE_")
                        && ATTEMPTS.computeIfAbsent(text, ignored -> new java.util.concurrent.atomic.AtomicInteger())
                        .incrementAndGet() == 1) {
                    throw new TextClassificationException(
                            "ai_not_configured",
                            "simulated provider configuration failure",
                            TextClassificationFailureClass.PROVIDER_TERMINAL
                    );
                }
                java.util.List<ClassifiedTextItem> items = "CANCELLED_INPUT".equals(text)
                        ? java.util.List.of()
                        : java.util.List.of(new ClassifiedTextItem(text, CaptureCategory.TASK));
                return new CaptureAnalysis(items, "test", "test-model", "v4");
            };
        }
    }
}

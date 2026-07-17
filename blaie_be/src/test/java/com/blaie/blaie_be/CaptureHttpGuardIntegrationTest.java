package com.blaie.blaie_be;

import com.blaie.blaie_be.auth.infrastructure.security.AuthTokenService;
import com.blaie.blaie_be.core.request.RequestContextFilter;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "blaie.capture.processing.accept-async-enabled=true",
        "blaie.capture.processing.publisher-enabled=true",
        "blaie.capture.processing.worker-enabled=false",
        "blaie.capture.processing.recovery-enabled=false",
        "blaie.capture.processing.max-active-jobs-per-user=2",
        "blaie.capture.processing.max-active-jobs-total=2",
        "blaie.capture.processing.max-oldest-queued-age=1h",
        "blaie.capture.processing.admission-retry-after=17s",
        "blaie.ai.concurrency.enabled=false",
        "blaie.rate-limit.key-prefix=test:capture-http-guard",
        "blaie.rate-limit.subject-hmac-secret=capture-http-guard-hmac-secret-at-least-32-bytes",
        "blaie.rate-limit.capture-text.windows[0].permit-limit=2",
        "blaie.rate-limit.capture-text.windows[0].window=10m",
        "blaie.rate-limit.capture-text.windows[1].permit-limit=100",
        "blaie.rate-limit.capture-text.windows[1].window=1h",
        "blaie.auth.access-token-secret=capture-http-guard-access-secret-at-least-32-bytes",
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
class CaptureHttpGuardIntegrationTest {
    private static final String RATE_LIMIT_KEY_PATTERN = "test:capture-http-guard:*";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestContextFilter requestContextFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    @BeforeEach
    void cleanState() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestContextFilter)
                .apply(springSecurity())
                .build();
        deleteRateLimitKeys();
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
    void nthCaptureIsAcceptedAndNextRequestIsRateLimitedWithoutWritingAnything() throws Exception {
        String accessToken = verifiedAccessToken("rate-limited-user");

        performCapture(accessToken, "first capture")
                .andExpect(status().isAccepted());
        performCapture(accessToken, "second capture")
                .andExpect(status().isAccepted());
        WorkflowCounts beforeRejectedRequest = workflowCounts();

        performCapture(accessToken, "must not be persisted")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        assertThat(workflowCounts()).isEqualTo(beforeRejectedRequest);
        assertThat(beforeRejectedRequest).isEqualTo(new WorkflowCounts(2, 2, 2, 2, 0));
    }

    @Test
    void globalAdmissionLimitReturnsServiceUnavailableAndRollsBackTheWholeWorkflow() throws Exception {
        String activeUserToken = verifiedAccessToken("active-overload-user");

        performCapture(activeUserToken, "active capture one")
                .andExpect(status().isAccepted());
        performCapture(activeUserToken, "active capture two")
                .andExpect(status().isAccepted());
        WorkflowCounts beforeRejectedRequest = workflowCounts();
        String rejectedUserToken = verifiedAccessToken("rejected-overload-user");

        performCapture(rejectedUserToken, "must be rejected by admission")
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "17"))
                .andExpect(jsonPath("$.code").value("CAPTURE_PROCESSING_OVERLOADED"));

        assertThat(workflowCounts()).isEqualTo(beforeRejectedRequest);
        assertThat(beforeRejectedRequest).isEqualTo(new WorkflowCounts(2, 2, 2, 2, 0));
    }

    private org.springframework.test.web.servlet.ResultActions performCapture(String accessToken, String text)
            throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/captures/text")
                .header("Authorization", "Bearer " + accessToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}");
        return mockMvc.perform(request);
    }

    private String verifiedAccessToken(String username) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, username, username_normalized, display_name) values (?, ?, ?, ?)",
                userId,
                username,
                username,
                "Capture Guard Test User"
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
        return authTokenService.issueAccessToken(userId);
    }

    private WorkflowCounts workflowCounts() {
        return new WorkflowCounts(
                count("captures"),
                count("processing_jobs"),
                count("capture_idempotency_keys"),
                count("event_publication"),
                count("capture_items")
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private void deleteRateLimitKeys() {
        Set<String> keys = redisTemplate.keys(RATE_LIMIT_KEY_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private record WorkflowCounts(
            int captures,
            int processingJobs,
            int idempotencyKeys,
            int outboxPublications,
            int captureItems
    ) {
    }
}

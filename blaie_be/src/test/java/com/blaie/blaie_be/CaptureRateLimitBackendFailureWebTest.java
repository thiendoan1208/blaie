package com.blaie.blaie_be;

import com.blaie.blaie_be.auth.infrastructure.security.AuthTokenService;
import com.blaie.blaie_be.core.ratelimit.limiter.RateLimitBackendUnavailableException;
import com.blaie.blaie_be.core.ratelimit.limiter.RateLimiter;
import com.blaie.blaie_be.core.request.RequestContextFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
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
        "blaie.ai.concurrency.enabled=false",
        "blaie.rate-limit.subject-hmac-secret=capture-backend-failure-hmac-secret-at-least-32-bytes",
        "blaie.auth.access-token-secret=capture-backend-failure-access-secret-at-least-32-bytes",
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
class CaptureRateLimitBackendFailureWebTest {
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestContextFilter requestContextFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    @MockitoBean
    private RateLimiter rateLimiter;

    @BeforeEach
    void cleanState() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestContextFilter)
                .apply(springSecurity())
                .build();
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
    void captureFailsClosedWhenRateLimitBackendIsUnavailableWithoutWritingAnything() throws Exception {
        String accessToken = verifiedAccessToken("backend-failure-user");
        when(rateLimiter.check(argThat(request -> "capture-text".equals(request.policyName()))))
                .thenThrow(new RateLimitBackendUnavailableException(new IllegalStateException("Redis unavailable")));

        mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"must not be persisted\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

        assertThat(count("captures")).isZero();
        assertThat(count("processing_jobs")).isZero();
        assertThat(count("capture_idempotency_keys")).isZero();
        assertThat(count("event_publication")).isZero();
        assertThat(count("capture_items")).isZero();
    }

    private String verifiedAccessToken(String username) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, username, username_normalized, display_name) values (?, ?, ?, ?)",
                userId,
                username,
                username,
                "Capture Backend Failure Test User"
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

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }
}

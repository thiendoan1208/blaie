package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@Import({TestcontainersConfiguration.class, InboxWebFlowTest.ClassifierTestConfiguration.class})
@SpringBootTest(properties = {
        "blaie.ai.provider=stub",
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
        jdbcTemplate.execute("delete from capture_items");
        jdbcTemplate.execute("delete from auth_action_tokens");
        jdbcTemplate.execute("delete from refresh_tokens");
        jdbcTemplate.execute("delete from auth_identities");
        jdbcTemplate.execute("delete from users");
    }

    @Test
    void verifiedUserCanCaptureAndOnlyOwnerCanReadTheItem() throws Exception {
        String ownerUsername = uniqueValue("inbox-owner");
        String ownerToken = registeredAndVerifiedAccessToken(ownerUsername);

        MvcResult capture = mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Send the proposal tomorrow\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalText").value("Send the proposal tomorrow"))
                .andExpect(jsonPath("$.data.processingStatus").value("completed"))
                .andExpect(jsonPath("$.data.items[0].category").value("task"))
                .andReturn();
        String itemId = capture.getResponse().getContentAsString()
                .replaceAll(".*\\\"items\\\":\\[\\{\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertThat(UUID.fromString(itemId)).isNotNull();

        mockMvc.perform(get("/api/v1/inbox")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(itemId))
                .andExpect(jsonPath("$.meta.hasMore").value(false));

        String otherToken = registeredAndVerifiedAccessToken(uniqueValue("inbox-other"));
        mockMvc.perform(get("/api/v1/inbox/items/{itemId}", itemId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAPTURE_ITEM_NOT_FOUND"));
    }

    @Test
    void unverifiedUserCannotCaptureText() throws Exception {
        String username = uniqueValue("inbox-unverified");
        MvcResult registered = register(username);

        mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + cookieValue(registered, ACCESS_COOKIE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Pay rent\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void cancelledCaptureCompletesWithoutCreatingAnInboxItem() throws Exception {
        String accessToken = registeredAndVerifiedAccessToken(uniqueValue("inbox-cancelled"));

        mockMvc.perform(post("/api/v1/captures/text")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"CANCELLED_INPUT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.processingStatus").value("completed"))
                .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(get("/api/v1/inbox")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
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

    private String json(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    @TestConfiguration
    static class ClassifierTestConfiguration {
        @Bean
        @Primary
        TextClassifierPort testTextClassifier() {
            return text -> {
                java.util.List<ClassifiedTextItem> items = "CANCELLED_INPUT".equals(text)
                        ? java.util.List.of()
                        : java.util.List.of(new ClassifiedTextItem(text, CaptureCategory.TASK));
                return new CaptureAnalysis(items, "test", "test-model", "v4");
            };
        }
    }
}

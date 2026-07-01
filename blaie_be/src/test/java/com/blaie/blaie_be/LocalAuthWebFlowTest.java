package com.blaie.blaie_be;

import com.blaie.blaie_be.core.request.RequestContextFilter;
import com.blaie.blaie_be.auth.domain.AuthActionTokenType;
import com.blaie.blaie_be.auth.infrastructure.security.AuthTokenService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@Import({TestcontainersConfiguration.class, LocalAuthWebFlowTest.SecurityTestController.class})
@SpringBootTest(properties = {
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
class LocalAuthWebFlowTest {
    private static final String ACCESS_COOKIE = "blaie_at";
    private static final String REFRESH_COOKIE = "blaie_rt";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String ACCESS_TOKEN_SECRET = "test-access-secret-that-is-at-least-32-bytes";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RequestContextFilter requestContextFilter;

    @Autowired
    private Clock clock;

    @Autowired
    private AuthTokenService authTokenService;

    @BeforeEach
    void cleanDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestContextFilter)
                .apply(springSecurity())
                .build();
        jdbcTemplate.execute("delete from auth_action_tokens");
        jdbcTemplate.execute("delete from refresh_tokens");
        jdbcTemplate.execute("delete from auth_identities");
        jdbcTemplate.execute("delete from users");
    }

    @Test
    void anonymousMeReturnsJsonUnauthorizedWithRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(REQUEST_ID_HEADER, "request-anonymous-me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(REQUEST_ID_HEADER, "request-anonymous-me"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.requestId").value("request-anonymous-me"));
    }

    @Test
    void invalidAndExpiredAccessTokensReturnUnauthorized() throws Exception {
        String username = uniqueValue("expired");
        String email = uniqueValue("expired") + "@example.com";
        registerUser(username, email, "Expired User", "Password1!");
        UUID userId = jdbcTemplate.queryForObject(
                "select id from users where username_normalized = ?",
                UUID.class,
                username.toLowerCase()
        );
        Instant now = clock.instant();
        String expiredToken = signedAccessToken(userId, now.minusSeconds(120), now.minusSeconds(60));

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new MockCookie(ACCESS_COOKIE, "malformed-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void csrfBootstrapIsPublicAndSetsReadableCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.headerName").value(CSRF_HEADER))
                .andReturn();

        jakarta.servlet.http.Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE);
        Assertions.assertThat(csrfCookie).isNotNull();
        Assertions.assertThat(csrfCookie.isHttpOnly()).isFalse();
        Assertions.assertThat(csrfCookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void cookieWritesRequireCsrfButBearerWritesDoNot() throws Exception {
        String username = uniqueValue("csrf");
        String email = uniqueValue("csrf") + "@example.com";
        registerUser(username, email, "Csrf User", "Password1!");
        markEmailVerified(username);
        MvcResult loginResult = loginWithIdentifier(username, "Password1!");
        String accessToken = cookieValue(loginResult, ACCESS_COOKIE);

        mockMvc.perform(post("/api/v1/test/write")
                        .cookie(new MockCookie(ACCESS_COOKIE, accessToken))
                        .header(REQUEST_ID_HEADER, "request-csrf-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").value("request-csrf-denied"));

        mockMvc.perform(withCsrf(post("/api/v1/test/write")
                        .cookie(new MockCookie(ACCESS_COOKIE, accessToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/test/write")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void authenticatedUserWithoutPermissionReturnsJsonForbidden() throws Exception {
        String username = uniqueValue("permission");
        String email = uniqueValue("permission") + "@example.com";
        registerUser(username, email, "Permission User", "Password1!");
        markEmailVerified(username);
        MvcResult loginResult = loginWithIdentifier(username, "Password1!");

        mockMvc.perform(get("/api/v1/test/permission")
                        .cookie(new MockCookie(ACCESS_COOKIE, cookieValue(loginResult, ACCESS_COOKIE)))
                        .header(REQUEST_ID_HEADER, "request-permission-denied"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(REQUEST_ID_HEADER, "request-permission-denied"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").value("request-permission-denied"));
    }

    @Test
    void corsPreflightAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type," + CSRF_HEADER))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void googleStartRedirectsToGoogleAndSetsStateCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/google/start")
                        .queryParam("next", "/tasks"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("client_id=test-google-client-id")))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("redirect_uri=http://localhost:8080/api/v1/auth/google/callback")))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("code_challenge_method=S256")))
                .andReturn();

        Assertions.assertThat(setCookieHeader(result, "blaie_google_oauth")).contains("HttpOnly");
    }

    @Test
    void registerLocalCreatesAccountAndSetsCookies() throws Exception {
        String username = uniqueValue("jane");
        String email = uniqueValue("jane") + "@example.com";

        MvcResult result = mockMvc.perform(withCsrf(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", email,
                                "displayName", "Jane Doe",
                                "password", "Password1!"
                        )))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.emailVerified").value(false))
                .andExpect(jsonPath("$.data.user.hasPassword").value(true))
                .andExpect(jsonPath("$.data.user.displayName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.user.admin").doesNotExist())
                .andReturn();

        assertHttpOnlyCookie(result, ACCESS_COOKIE);
        assertHttpOnlyCookie(result, REFRESH_COOKIE);
    }

    @Test
    void loginLocalAcceptsEmailAndReturnsAuthenticatedUser() throws Exception {
        String username = uniqueValue("lucas");
        String email = uniqueValue("lucas") + "@example.com";
        registerUser(username, email, "Lucas Ray", "Password1!");

        MvcResult result = loginWithIdentifier(email, "Password1!");

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new MockCookie(ACCESS_COOKIE, cookieValue(result, ACCESS_COOKIE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.emailVerified").value(false))
                .andExpect(jsonPath("$.data.user.hasPassword").value(true));
    }

    @Test
    void accountSettingsUpdateUsernameAndPassword() throws Exception {
        String username = uniqueValue("settings");
        String newUsername = uniqueValue("settings-new");
        String email = uniqueValue("settings") + "@example.com";
        MvcResult registerResult = registerUser(username, email, "Settings User", "Password1!");
        markEmailVerified(username);
        String accessToken = cookieValue(registerResult, ACCESS_COOKIE);
        String refreshToken = cookieValue(registerResult, REFRESH_COOKIE);

        mockMvc.perform(withCsrf(patch("/api/v1/auth/me/username")
                        .cookie(new MockCookie(ACCESS_COOKIE, accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", newUsername)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(newUsername))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.hasPassword").value(true));

        mockMvc.perform(withCsrf(patch("/api/v1/auth/me/password")
                        .cookie(new MockCookie(ACCESS_COOKIE, accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", "Password1!",
                                "newPassword", "Password2@"
                        )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(newUsername))
                .andExpect(jsonPath("$.data.user.hasPassword").value(true));

        mockMvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "identifier", newUsername,
                                "password", "Password1!"
                        )))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie(REFRESH_COOKIE, refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        loginWithIdentifier(newUsername, "Password2@");
    }

    @Test
    void passwordResetChangesPasswordAndRevokesActiveRefreshTokens() throws Exception {
        String username = uniqueValue("reset");
        String email = uniqueValue("reset") + "@example.com";
        MvcResult registerResult = registerUser(username, email, "Reset User", "Password1!");
        String oldRefreshToken = cookieValue(registerResult, REFRESH_COOKIE);
        UUID userId = jdbcTemplate.queryForObject(
                "select id from users where username_normalized = ?",
                UUID.class,
                username.toLowerCase()
        );
        String resetCode = "123456";
        jdbcTemplate.update(
                "insert into auth_action_tokens (id, user_id, type, token_hash, expires_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                AuthActionTokenType.PASSWORD_RESET,
                authTokenService.hashOpaqueToken(userId + ":" + AuthActionTokenType.PASSWORD_RESET + ":" + resetCode),
                Timestamp.from(clock.instant().plusSeconds(900))
        );

        mockMvc.perform(withCsrf(post("/api/v1/auth/password-reset/confirm")
                        .cookie(new MockCookie(REFRESH_COOKIE, oldRefreshToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "code", resetCode,
                                "newPassword", "Password2@"
                        )))))
                .andExpect(status().isNoContent());

        mockMvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "identifier", email,
                                "password", "Password1!"
                        )))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        loginWithIdentifier(email, "Password2@");

        mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie(REFRESH_COOKIE, oldRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void unverifiedUserCannotAccessAppApisButCanReadMe() throws Exception {
        String username = uniqueValue("unverified");
        String email = uniqueValue("unverified") + "@example.com";
        MvcResult registerResult = registerUser(username, email, "Unverified User", "Password1!");
        String accessToken = cookieValue(registerResult, ACCESS_COOKIE);

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new MockCookie(ACCESS_COOKIE, accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.emailVerified").value(false));

        mockMvc.perform(withCsrf(post("/api/v1/test/write")
                        .cookie(new MockCookie(ACCESS_COOKIE, accessToken))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.message").value("Email not verified"));
    }

    @Test
    void meAcceptsBearerAccessTokenFromCookieValue() throws Exception {
        String username = uniqueValue("mira");
        String email = uniqueValue("mira") + "@example.com";
        registerUser(username, email, "Mira Stone", "Password1!");

        MvcResult loginResult = loginWithIdentifier(username, "Password1!");
        String accessToken = cookieValue(loginResult, ACCESS_COOKIE);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andExpect(jsonPath("$.data.user.email").value(email));
    }

    @Test
    void meAcceptsBearerAccessTokenIgnoreCase() throws Exception {
        String username = uniqueValue("mira-case");
        String email = uniqueValue("mira-case") + "@example.com";
        registerUser(username, email, "Mira Stone Case", "Password1!");

        MvcResult loginResult = loginWithIdentifier(username, "Password1!");
        String accessToken = cookieValue(loginResult, ACCESS_COOKIE);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(username));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "BEARER " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(username));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "bEaReR " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(username));
    }

    @Test
    void csrfBypassAcceptsBearerIgnoreCase() throws Exception {
        String username = uniqueValue("csrf-case");
        String email = uniqueValue("csrf-case") + "@example.com";
        registerUser(username, email, "Csrf Case User", "Password1!");
        markEmailVerified(username);
        MvcResult loginResult = loginWithIdentifier(username, "Password1!");
        String accessToken = cookieValue(loginResult, ACCESS_COOKIE);

        mockMvc.perform(post("/api/v1/test/write")
                        .header(HttpHeaders.AUTHORIZATION, "bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/test/write")
                        .header(HttpHeaders.AUTHORIZATION, "BEARER " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void refreshRotatesRefreshTokenAndRejectsOldToken() throws Exception {
        String username = uniqueValue("oliver");
        String email = uniqueValue("oliver") + "@example.com";
        registerUser(username, email, "Oliver Lane", "Password1!");

        MvcResult loginResult = loginWithIdentifier(username, "Password1!");
        String oldRefreshToken = cookieValue(loginResult, REFRESH_COOKIE);

        MvcResult refreshResult = mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie(REFRESH_COOKIE, oldRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andReturn();
        String newRefreshToken = cookieValue(refreshResult, REFRESH_COOKIE);

        assertHttpOnlyCookie(refreshResult, ACCESS_COOKIE);
        assertHttpOnlyCookie(refreshResult, REFRESH_COOKIE);

        mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie(REFRESH_COOKIE, oldRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie(REFRESH_COOKIE, newRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void logoutClearsCookiesAndRevokesRefreshToken() throws Exception {
        String username = uniqueValue("ava");
        String email = uniqueValue("ava") + "@example.com";
        registerUser(username, email, "Ava Rose", "Password1!");

        MvcResult loginResult = loginWithIdentifier(email, "Password1!");
        String refreshToken = cookieValue(loginResult, REFRESH_COOKIE);

        mockMvc.perform(withCsrf(post("/api/v1/auth/logout")
                        .cookie(new MockCookie(REFRESH_COOKIE, refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie(REFRESH_COOKIE, refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void logoutWorksWithExpiredAccessTokenAndIsIdempotent() throws Exception {
        String username = uniqueValue("logout-expired");
        String email = uniqueValue("logout-expired") + "@example.com";
        registerUser(username, email, "Logout Expired", "Password1!");
        MvcResult loginResult = loginWithIdentifier(username, "Password1!");
        String refreshToken = cookieValue(loginResult, REFRESH_COOKIE);
        UUID userId = jdbcTemplate.queryForObject(
                "select id from users where username_normalized = ?",
                UUID.class,
                username.toLowerCase()
        );
        Instant now = clock.instant();
        String expiredAccessToken = signedAccessToken(userId, now.minusSeconds(120), now.minusSeconds(60));

        MvcResult logoutResult = mockMvc.perform(withCsrf(post("/api/v1/auth/logout")
                        .cookie(
                                new MockCookie(ACCESS_COOKIE, expiredAccessToken),
                                new MockCookie(REFRESH_COOKIE, refreshToken)
                        )))
                .andExpect(status().isNoContent())
                .andReturn();
        Assertions.assertThat(setCookieHeader(logoutResult, ACCESS_COOKIE)).contains("Max-Age=0");
        Assertions.assertThat(setCookieHeader(logoutResult, REFRESH_COOKIE)).contains("Max-Age=0");

        mockMvc.perform(withCsrf(post("/api/v1/auth/logout")
                        .cookie(new MockCookie(REFRESH_COOKIE, refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(withCsrf(post("/api/v1/auth/logout")))
                .andExpect(status().isNoContent());
    }

    @Test
    void disabledUserRefreshRevokesAllActiveSessions() throws Exception {
        String username = uniqueValue("disabled");
        String email = uniqueValue("disabled") + "@example.com";
        registerUser(username, email, "Disabled User", "Password1!");
        String firstRefreshToken = cookieValue(loginWithIdentifier(username, "Password1!"), REFRESH_COOKIE);
        loginWithIdentifier(email, "Password1!");
        UUID userId = jdbcTemplate.queryForObject(
                "select id from users where username_normalized = ?",
                UUID.class,
                username.toLowerCase()
        );
        jdbcTemplate.update("update users set status = 'disabled' where id = ?", userId);

        mockMvc.perform(withCsrf(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie(REFRESH_COOKIE, firstRefreshToken))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Integer activeSessions = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is null",
                Integer.class,
                userId
        );
        Assertions.assertThat(activeSessions).isZero();
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        String username = uniqueValue("zoe");
        String firstEmail = uniqueValue("zoe") + "@example.com";
        String secondEmail = uniqueValue("zoe") + "@example.net";

        registerUser(username, firstEmail, "Zoe Hart", "Password1!");

        mockMvc.perform(withCsrf(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", secondEmail,
                                "displayName", "Zoe Hart 2",
                                "password", "Password1!"
                        )))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    private MvcResult registerUser(String username, String email, String displayName, String password) throws Exception {
        return mockMvc.perform(withCsrf(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", email,
                                "displayName", displayName,
                                "password", password
                        )))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult loginWithIdentifier(String identifier, String password) throws Exception {
        return mockMvc.perform(withCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "identifier", identifier,
                                "password", password
                        )))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void markEmailVerified(String username) {
        jdbcTemplate.update("""
                update auth_identities
                   set email_verified = true
                  from users
                 where auth_identities.user_id = users.id
                   and users.username_normalized = ?
                """, username.toLowerCase());
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = cookieValue(csrfResult, CSRF_COOKIE);
        return requestBuilder
                .cookie(new MockCookie(CSRF_COOKIE, csrfToken))
                .header(CSRF_HEADER, csrfToken);
    }

    private String cookieValue(MvcResult result, String cookieName) {
        String header = setCookieHeader(result, cookieName);
        String prefix = cookieName + "=";
        int valueEnd = header.indexOf(';');
        return valueEnd < 0 ? header.substring(prefix.length()) : header.substring(prefix.length(), valueEnd);
    }

    private void assertHttpOnlyCookie(MvcResult result, String cookieName) {
        Assertions.assertThat(setCookieHeader(result, cookieName)).contains("HttpOnly");
    }

    private String setCookieHeader(MvcResult result, String cookieName) {
        String prefix = cookieName + "=";
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Set-Cookie header for " + cookieName));
    }

    private String uniqueValue(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String signedAccessToken(UUID userId, Instant issuedAt, Instant expiresAt) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"v1\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("""
                {"typ":"access","sub":"%s","iss":"blaie-api","aud":"blaie-clients","jti":"%s","iat":%d,"exp":%d}
                """).formatted(userId, UUID.randomUUID(), issuedAt.getEpochSecond(), expiresAt.getEpochSecond())
                .trim().getBytes(StandardCharsets.UTF_8));
        String unsignedToken = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(ACCESS_TOKEN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return unsignedToken + "." + encoder.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
    }

    private String json(Map<String, Object> body) {
        return body.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + escapeJson(String.valueOf(entry.getValue())) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @RestController
    static class SecurityTestController {
        @PostMapping("/api/v1/test/write")
        ResponseEntity<Void> write() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/v1/test/permission")
        @PreAuthorize("@defaultAuthorizationService.can(T(com.blaie.blaie_be.authz.domain.PermissionAction).ITEM_READ)")
        ResponseEntity<Void> permission() {
            return ResponseEntity.noContent().build();
        }
    }
}

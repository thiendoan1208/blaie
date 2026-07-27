package com.blaie.blaie_be;

import com.blaie.blaie_be.auth.infrastructure.security.AuthTokenService;
import com.blaie.blaie_be.capture.application.port.SpeechToTextPort;
import com.blaie.blaie_be.core.request.RequestContextFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "blaie.capture.processing.accept-async-enabled=false",
        "blaie.capture.processing.publisher-enabled=false",
        "blaie.capture.processing.worker-enabled=false",
        "blaie.capture.processing.recovery-enabled=false",
        "blaie.ai.concurrency.enabled=false",
        "blaie.rate-limit.enabled=false",
        "blaie.auth.access-token-secret=transcription-http-guard-secret-at-least-32-bytes",
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
class AudioTranscriptionHttpGuardIntegrationTest {
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
    private SpeechToTextPort speechToText;

    @BeforeEach
    void cleanState() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestContextFilter)
                .apply(springSecurity())
                .build();
        reset(speechToText);
        jdbcTemplate.execute("delete from audit_events");
        jdbcTemplate.execute("delete from auth_identities");
        jdbcTemplate.execute("delete from users");
    }

    @Test
    void authenticationIsRequiredBeforeAnyAudioReachesTheProvider() throws Exception {
        mockMvc.perform(multipart("/api/v1/transcriptions/audio")
                        .file(audio())
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(speechToText);
    }

    @Test
    void verifiedEmailIsRequiredBeforeAnyAudioReachesTheProvider() throws Exception {
        String token = accessToken("unverified-voice-user", false);

        mockMvc.perform(multipart("/api/v1/transcriptions/audio")
                        .file(audio())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        verifyNoInteractions(speechToText);
    }

    @Test
    void verifiedStandardUserCanUseCaptureCreatePermission() throws Exception {
        String token = accessToken("verified-voice-user", true);
        when(speechToText.transcribe(any(), eq("vi")))
                .thenReturn("Nhắc tôi gọi cho mẹ");

        mockMvc.perform(multipart("/api/v1/transcriptions/audio")
                        .file(audio())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value("Nhắc tôi gọi cho mẹ"));

        verify(speechToText).transcribe(any(), eq("vi"));
    }

    private MockMultipartFile audio() {
        return new MockMultipartFile(
                "file",
                "voice.webm",
                "audio/webm",
                new byte[]{1, 2, 3}
        );
    }

    private String accessToken(String username, boolean verified) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, username, username_normalized, display_name) values (?, ?, ?, ?)",
                userId,
                username,
                username,
                "Voice Guard Test User"
        );
        jdbcTemplate.update("""
                insert into auth_identities (
                    id, user_id, provider, email_verified, password_hash
                ) values (?, ?, 'local', ?, ?)
                """,
                UUID.randomUUID(),
                userId,
                verified,
                "not-used-by-this-test"
        );
        return authTokenService.issueAccessToken(userId);
    }
}

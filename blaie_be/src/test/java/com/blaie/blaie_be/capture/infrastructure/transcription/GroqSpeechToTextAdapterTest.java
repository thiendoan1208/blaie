package com.blaie.blaie_be.capture.infrastructure.transcription;

import com.blaie.blaie_be.capture.application.port.AudioInput;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GroqSpeechToTextAdapterTest {
    private static final String ENDPOINT = "https://groq.test/audio/transcriptions";

    private GroqTranscriptionProperties properties;
    private MockRestServiceServer server;
    private GroqSpeechToTextAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new GroqTranscriptionProperties();
        properties.setApiKey("test-groq-key");
        properties.setModel("whisper-test-model");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://groq.test");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GroqSpeechToTextAdapter(properties, builder.build(), new ObjectMapper());
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void sendsMultipartAudioAndReturnsOnlyTranscriptText() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-groq-key"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.MULTIPART_FORM_DATA_VALUE)))
                .andExpect(content().string(containsString("name=\"file\"")))
                .andExpect(content().string(containsString("filename=\"voice.webm\"")))
                .andExpect(content().string(containsString("name=\"model\"")))
                .andExpect(content().string(containsString("whisper-test-model")))
                .andExpect(content().string(containsString("name=\"language\"")))
                .andExpect(content().string(containsString("vi")))
                .andExpect(content().string(containsString("name=\"response_format\"")))
                .andExpect(content().string(containsString("json")))
                .andExpect(content().string(containsString("name=\"temperature\"")))
                .andExpect(content().string(containsString("0")))
                .andRespond(withSuccess("{\"text\":\"Nhắc tôi gọi cho mẹ\"}", MediaType.APPLICATION_JSON));

        String text = adapter.transcribe(
                new AudioInput("voice.webm", "audio/webm", new byte[]{1, 2, 3}),
                "vi"
        );

        assertThat(text).isEqualTo("Nhắc tôi gọi cho mẹ");
    }

    @Test
    void missingApiKeyFailsWithoutMakingAProviderRequest() {
        properties.setApiKey(" ");

        assertError(ErrorCode.TRANSCRIPTION_NOT_CONFIGURED);
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 503})
    void transientProviderResponsesAreUnavailable(int status) {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)));

        assertError(ErrorCode.TRANSCRIPTION_UNAVAILABLE);
    }

    @Test
    void transportTimeoutIsUnavailable() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withException(new SocketTimeoutException("simulated timeout")));

        assertError(ErrorCode.TRANSCRIPTION_UNAVAILABLE);
    }

    @Test
    void malformedProviderResponseIsUnavailable() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertError(ErrorCode.TRANSCRIPTION_UNAVAILABLE);
    }

    private void assertError(ErrorCode errorCode) {
        AudioInput input = new AudioInput("voice.webm", "audio/webm", new byte[]{1});
        assertThatThrownBy(() -> adapter.transcribe(input, "vi"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(errorCode));
    }
}

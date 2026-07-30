package com.blaie.blaie_be.capture.infrastructure.gemini;

import com.blaie.blaie_be.capture.application.port.ImageAnalysisInput;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiImageAnalyzerAdapterTest {
    private static final String ENDPOINT =
            "https://gemini.test/models/gemini-test:generateContent";
    private static final String AUTH_KEY = "AQ.Ab-test-auth-key";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private GeminiImageAnalyzerAdapter adapter;

    @BeforeEach
    void setUp() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey(AUTH_KEY);
        properties.setModel("gemini-test");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GeminiImageAnalyzerAdapter(properties, builder.build(), objectMapper);
    }

    @AfterEach
    void verify() {
        server.verify();
    }

    @Test
    void sendsInlineImageAndOptionalTextWithoutToolsOrGrounding() {
        String response = objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "finishReason", "STOP",
                        "content", Map.of("parts", List.of(Map.of(
                                "text",
                                "{\"items\":[{\"text\":\"Buy paper\",\"category\":\"task\"}]}"
                        )))
                ))
        ));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", AUTH_KEY))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"text\":\"Please read this receipt\""),
                        org.hamcrest.Matchers.containsString("\"mimeType\":\"image/png\""),
                        org.hamcrest.Matchers.containsString("\"data\":\"AQID\""),
                        org.hamcrest.Matchers.containsString("\"responseMimeType\":\"application/json\""),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"tools\"")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"grounding\""))
                )))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        var analysis = adapter.analyze(new ImageAnalysisInput(
                "Please read this receipt",
                "image/png",
                new byte[]{1, 2, 3}
        ));

        assertThat(analysis.provider()).isEqualTo("gemini");
        assertThat(analysis.model()).isEqualTo("gemini-test");
        assertThat(analysis.items()).singleElement().satisfies(item -> {
            assertThat(item.originalText()).isEqualTo("Buy paper");
            assertThat(item.category()).isEqualTo(CaptureCategory.TASK);
        });
    }

    @Test
    void invalidStructuredOutputIsRetryableAndNeverFallsBack() {
        String response = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{}"}]}}]}
                """;
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.analyze(new ImageAnalysisInput(
                null,
                "image/png",
                new byte[]{1}
        ))).isInstanceOf(TextClassificationException.class)
                .satisfies(error -> {
                    var failure = (TextClassificationException) error;
                    assertThat(failure.failureCode()).isEqualTo("ai_invalid_response");
                    assertThat(failure.failureClass())
                            .isEqualTo(TextClassificationFailureClass.PROVIDER_RETRYABLE);
                });
    }

    @Test
    void transientProviderFailureIsRetryable() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatusCode.valueOf(503)));

        assertThatThrownBy(() -> adapter.analyze(new ImageAnalysisInput(
                null,
                "image/jpeg",
                new byte[]{1}
        ))).isInstanceOf(TextClassificationException.class)
                .satisfies(error -> assertThat(
                        ((TextClassificationException) error).failureCode()
                ).isEqualTo("ai_provider_unavailable"));
    }
}

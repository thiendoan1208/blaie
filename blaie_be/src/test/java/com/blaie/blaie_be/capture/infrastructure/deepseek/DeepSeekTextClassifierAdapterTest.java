package com.blaie.blaie_be.capture.infrastructure.deepseek;

import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekTextClassifierAdapterTest {
    private static final String ENDPOINT = "https://deepseek.test/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DeepSeekProperties properties;
    private MockRestServiceServer server;
    private DeepSeekTextClassifierAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        properties.setApiKey("test-api-key");
        properties.setModel("deepseek-test-model");

        RestClient.Builder builder = RestClient.builder().baseUrl("https://deepseek.test");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new DeepSeekTextClassifierAdapter(properties, builder.build(), objectMapper);
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void promptDefinesCurrentCaptureClassificationContract() {
        assertThat(DeepSeekTextClassifierAdapter.PROMPT_VERSION).isEqualTo("v5");
        assertThat(DeepSeekTextClassifierAdapter.SYSTEM_PROMPT)
                .contains("Split one personal Inbox capture into every independent record")
                .contains("Return JSON only")
                .contains("a request addressed to the assistant")
                .contains("The latest explicit decision wins")
                .contains("cancels, rejects, negates")
                .contains("Return {\"items\":[]} when the capture contains no active record")
                .contains("reminder: only when the user explicitly asks the system to remind or notify them")
                .contains("calendar_event: a scheduled meeting, appointment, or event")
                .contains("information: a question or a request addressed to the assistant")
                .contains("task: an action the user intends, needs, plans, or commits to perform themselves")
                .contains("preserve each __BLAIE_PII_ token from that record exactly")
                .contains("may be omitted only with a clause that is not emitted")
                .contains("Do not add markdown, explanations, or extra keys.");
    }

    @Test
    void validStructuredResponseProducesClassifiedItemsAndProviderMetadata() {
        expectCompletion(
                "{\"items\":[{\"text\":\"Buy milk\",\"category\":\"task\"}]}",
                "stop"
        );

        var analysis = adapter.classify("Buy milk");

        assertThat(analysis.provider()).isEqualTo("deepseek");
        assertThat(analysis.model()).isEqualTo("deepseek-test-model");
        assertThat(analysis.promptVersion()).isEqualTo("v5");
        assertThat(analysis.items()).hasSize(1);
        assertThat(analysis.items().getFirst().originalText()).isEqualTo("Buy milk");
        assertThat(analysis.items().getFirst().category()).isEqualTo(CaptureCategory.TASK);
    }

    @Test
    void missingApiKeyIsProviderTerminalSoAnotherProviderMayBeTried() {
        properties.setApiKey(" ");

        assertFailure(
                "ai_not_configured",
                TextClassificationFailureClass.PROVIDER_TERMINAL
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 503})
    void transientHttpFailuresAreProviderRetryable(int status) {
        expectStatus(status);

        assertFailure(
                "ai_provider_unavailable",
                TextClassificationFailureClass.PROVIDER_RETRYABLE
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404})
    void permanentProviderHttpFailuresAreTerminalOnlyForThatProvider(int status) {
        expectStatus(status);

        assertFailure(
                "ai_provider_rejected",
                TextClassificationFailureClass.PROVIDER_TERMINAL
        );
    }

    @Test
    void transportTimeoutIsProviderRetryable() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new SocketTimeoutException("simulated timeout")));

        assertFailure(
                "ai_provider_unavailable",
                TextClassificationFailureClass.PROVIDER_RETRYABLE
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "{\"records\":[]}",
            "{\"items\":[],\"extra\":true}",
            "{\"items\":[{\"text\":\"Buy milk\",\"category\":\"unknown\"}]}",
            "{\"items\":[{\"text\":\" \",\"category\":\"task\"}]}",
            "{\"items\":[{\"text\":\"Buy milk\",\"category\":\"task\",\"extra\":true}]}"
    })
    void malformedStructuredOutputIsProviderRetryable(String content) {
        expectCompletion(content, "stop");

        assertFailure(
                "ai_invalid_response",
                TextClassificationFailureClass.PROVIDER_RETRYABLE
        );
    }

    @Test
    void moreThanThirtyTwoItemsIsProviderRetryableInvalidOutput() {
        List<Map<String, String>> items = IntStream.range(0, 33)
                .mapToObj(index -> Map.of("text", "Item " + index, "category", "task"))
                .toList();
        expectCompletion(objectMapper.writeValueAsString(Map.of("items", items)), "stop");

        assertFailure(
                "ai_invalid_response",
                TextClassificationFailureClass.PROVIDER_RETRYABLE
        );
    }

    @Test
    void emptyChoicesIsProviderRetryableInvalidOutput() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertFailure(
                "ai_invalid_response",
                TextClassificationFailureClass.PROVIDER_RETRYABLE
        );
    }

    @Test
    void truncatedCompletionIsProviderRetryableEvenWhenContentLooksValid() {
        expectCompletion("{\"items\":[]}", "length");

        assertFailure(
                "ai_invalid_response",
                TextClassificationFailureClass.PROVIDER_RETRYABLE
        );
    }

    @Test
    void providerContentFilterIsTerminalForTheCapture() {
        expectCompletion("{\"items\":[]}", "content_filter");

        assertFailure(
                "content_policy_blocked",
                TextClassificationFailureClass.CONTENT_TERMINAL
        );
    }

    private void expectCompletion(String content, String finishReason) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("message", Map.of("content", content));
        choice.put("finish_reason", finishReason);
        String response = objectMapper.writeValueAsString(Map.of("choices", List.of(choice)));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void expectStatus(int status) {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatusCode.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));
    }

    private void assertFailure(
            String expectedCode,
            TextClassificationFailureClass expectedClass
    ) {
        assertThatThrownBy(() -> adapter.classify("Buy milk"))
                .isInstanceOf(TextClassificationException.class)
                .satisfies(exception -> {
                    TextClassificationException classificationException =
                            (TextClassificationException) exception;
                    assertThat(classificationException.failureCode()).isEqualTo(expectedCode);
                    assertThat(classificationException.failureClass()).isEqualTo(expectedClass);
                });
    }
}

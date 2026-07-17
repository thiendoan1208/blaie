package com.blaie.blaie_be.capture.infrastructure.deepseek;

import com.blaie.blaie_be.capture.application.port.TextClassifierProvider;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DeepSeekTextClassifierAdapter implements TextClassifierProvider {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekTextClassifierAdapter.class);
    static final String PROMPT_VERSION = "v4";
    static final String SYSTEM_PROMPT = """
            Split one personal Inbox capture into every independent record the user expressed, then classify each record.
            Return JSON only, exactly in this shape when active records exist:
            {"items":[{"text":"one atomic record in the user's language","category":"task"}]}
            Return {"items":[]} when the capture contains no active record worth creating.
            category must be exactly one of: task, calendar_event, reminder, information.

            Before splitting, determine the user's final intent from each complete clause. The latest explicit decision wins.
            Do not emit an item for an action that the user cancels, rejects, negates, says is no longer needed, or already
            completed. Do not turn background context, abandoned plans, or hypothetical examples into tasks.

            Classify by who is expected to act, using these rules in order:
            - reminder: only when the user explicitly asks the system to remind or notify them.
            - calendar_event: a scheduled meeting, appointment, or event the user will attend or be involved in.
            - information: a question or a request addressed to the assistant to explain, find, search, research, compare,
              summarize, or otherwise provide knowledge. In a chat, an imperative with no explicit subject is addressed to
              the assistant by default.
            - task: an action the user intends, needs, plans, or commits to perform themselves.


            Keep text concise, preserve dates/times and language, and never invent facts.
            Do not add markdown, explanations, or extra keys.
            """;

    private final DeepSeekProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DeepSeekTextClassifierAdapter(
            DeepSeekProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return "deepseek";
    }

    @Override
    public CaptureAnalysis classify(String text) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new TextClassificationException(
                    "ai_not_configured",
                    "DeepSeek API key is not configured",
                    false
            );
        }

        try {
            CompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(Map.of(
                            "model", properties.model(),
                            "messages", List.of(
                                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                                    Map.of("role", "user", "content", text)
                            ),
                            "response_format", Map.of("type", "json_object"),
                            "thinking", Map.of("type", "disabled"),
                            "temperature", 0,
                            "max_tokens", 512
                    ))
                    .retrieve()
                    .body(CompletionResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw invalidResponse("missing choices", null);
            }
            Choice choice = response.choices().getFirst();
            String content = choice.message() == null ? null : choice.message().content();
            return parseAnalysis(content, choice.finishReason());
        } catch (TextClassificationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            boolean retryable = status == 408 || status == 429 || status >= 500;
            throw new TextClassificationException(
                    retryable ? "ai_provider_unavailable" : "ai_provider_rejected",
                    "DeepSeek request was rejected",
                    retryable,
                    exception
            );
        } catch (RestClientException exception) {
            throw new TextClassificationException("ai_provider_unavailable", "DeepSeek request failed", exception);
        } catch (RuntimeException exception) {
            throw new TextClassificationException("ai_invalid_response", "DeepSeek response was invalid", exception);
        }
    }

    private CaptureAnalysis parseAnalysis(String content, String finishReason) {
        if (content == null || content.isBlank()) {
            throw invalidResponse("empty content", finishReason);
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.isObject() || root.size() != 1 || !root.has("items") || !root.get("items").isArray()) {
                throw invalidResponse("invalid root schema", finishReason);
            }
            JsonNode itemNodes = root.get("items");
            if (itemNodes.size() > 32) {
                throw invalidResponse("invalid item count", finishReason);
            }
            List<ClassifiedTextItem> items = new ArrayList<>();
            for (JsonNode itemNode : itemNodes) {
                if (!itemNode.isObject() || itemNode.size() != 2
                        || !itemNode.has("text") || !itemNode.get("text").isString()
                        || !itemNode.has("category") || !itemNode.get("category").isString()) {
                    throw invalidResponse("invalid item schema", finishReason);
                }
                String itemText = itemNode.get("text").asString().trim();
                if (itemText.isEmpty() || itemText.length() > 10_000) {
                    throw invalidResponse("invalid item text", finishReason);
                }
                items.add(new ClassifiedTextItem(itemText, CaptureCategory.fromValue(itemNode.get("category").asString())));
            }
            return new CaptureAnalysis(
                    items,
                    "deepseek",
                    properties.model(),
                    PROMPT_VERSION
            );
        } catch (TextClassificationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TextClassificationException("ai_invalid_response", "DeepSeek category was invalid", exception);
        }
    }

    private TextClassificationException invalidResponse(String reason, String finishReason) {
        log.warn("DeepSeek classification response rejected: reason={}, finishReason={}", reason, finishReason);
        return new TextClassificationException("ai_invalid_response", "DeepSeek response was invalid");
    }

    private record CompletionResponse(List<Choice> choices) {
    }

    private record Choice(Message message, @com.fasterxml.jackson.annotation.JsonProperty("finish_reason") String finishReason) {
    }

    private record Message(String content) {
    }
}

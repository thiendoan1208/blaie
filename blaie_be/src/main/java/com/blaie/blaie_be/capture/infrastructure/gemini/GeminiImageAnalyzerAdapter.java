package com.blaie.blaie_be.capture.infrastructure.gemini;

import com.blaie.blaie_be.capture.application.port.ImageAnalysisInput;
import com.blaie.blaie_be.capture.application.port.ImageAnalyzerProvider;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import com.blaie.blaie_be.capture.domain.CaptureAnalysisException;
import com.blaie.blaie_be.capture.domain.CaptureFailureClass;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GeminiImageAnalyzerAdapter implements ImageAnalyzerProvider {
    private static final Logger log = LoggerFactory.getLogger(GeminiImageAnalyzerAdapter.class);
    static final String PROMPT_VERSION = "image-v1";
    static final String SYSTEM_PROMPT = """
            Analyze the attached image and optional user text as one personal Inbox capture.
            Extract every independent active record and classify it as exactly one of:
            task, calendar_event, reminder, information.
            Use the image as evidence, preserve the user's language and dates/times, and never invent unreadable facts.
            The optional text and image form one request. Do not ignore either input.
            Return only the requested JSON schema. Preserve every __BLAIE_PII_ token exactly.
            """;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "items", Map.of(
                            "type", "array",
                            "maxItems", 32,
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "text", Map.of("type", "string"),
                                            "category", Map.of(
                                                    "type", "string",
                                                    "enum", List.of(
                                                            "task",
                                                            "calendar_event",
                                                            "reminder",
                                                            "information"
                                                    )
                                            )
                                    ),
                                    "required", List.of("text", "category"),
                                    "additionalProperties", false
                            )
                    )
            ),
            "required", List.of("items"),
            "additionalProperties", false
    );

    private final GeminiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiImageAnalyzerAdapter(
            GeminiProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
    }

    GeminiImageAnalyzerAdapter(
            GeminiProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return "gemini";
    }

    @Override
    public CaptureAnalysis analyze(ImageAnalysisInput input) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw failure(
                    "ai_not_configured",
                    CaptureFailureClass.PROVIDER_TERMINAL,
                    null
            );
        }
        try {
            GeminiResponse response = restClient.post()
                    .uri(builder -> builder
                            .path("/models/{model}:generateContent")
                            .build(properties.model()))
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(input))
                    .retrieve()
                    .body(GeminiResponse.class);
            return parse(response);
        } catch (CaptureAnalysisException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            boolean retryable = status == 408 || status == 429 || status >= 500;
            throw failure(
                    retryable ? "ai_provider_unavailable" : "ai_provider_rejected",
                    retryable
                            ? CaptureFailureClass.PROVIDER_RETRYABLE
                            : CaptureFailureClass.PROVIDER_TERMINAL,
                    exception
            );
        } catch (RestClientException exception) {
            throw failure(
                    "ai_provider_unavailable",
                    CaptureFailureClass.PROVIDER_RETRYABLE,
                    exception
            );
        } catch (RuntimeException exception) {
            throw failure(
                    "ai_invalid_response",
                    CaptureFailureClass.PROVIDER_RETRYABLE,
                    exception
            );
        }
    }

    private Map<String, Object> request(ImageAnalysisInput input) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", SYSTEM_PROMPT));
        if (input.text() != null && !input.text().isBlank()) {
            parts.add(Map.of("text", input.text()));
        }
        parts.add(Map.of("inlineData", Map.of(
                "mimeType", input.contentType(),
                "data", Base64.getEncoder().encodeToString(input.imageBytes())
        )));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        request.put("generationConfig", Map.of(
                "temperature", 0,
                "maxOutputTokens", 1024,
                "responseMimeType", "application/json",
                "responseJsonSchema", RESPONSE_SCHEMA
        ));
        return request;
    }

    private CaptureAnalysis parse(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw invalid("missing candidates");
        }
        Candidate candidate = response.candidates().getFirst();
        if (!"STOP".equalsIgnoreCase(candidate.finishReason())) {
            throw invalid("non-terminal finish reason");
        }
        if (candidate.content() == null
                || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            throw invalid("missing content");
        }
        String content = candidate.content().parts().getFirst().text();
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.isObject() || root.size() != 1 || !root.has("items") || !root.get("items").isArray()) {
                throw invalid("invalid root schema");
            }
            if (root.get("items").size() > 32) {
                throw invalid("invalid item count");
            }
            List<ClassifiedTextItem> items = new ArrayList<>();
            for (JsonNode node : root.get("items")) {
                if (!node.isObject() || node.size() != 2
                        || !node.has("text") || !node.get("text").isString()
                        || !node.has("category") || !node.get("category").isString()) {
                    throw invalid("invalid item schema");
                }
                String text = node.get("text").asString().trim();
                if (text.isBlank() || text.length() > 10_000) {
                    throw invalid("invalid item text");
                }
                items.add(new ClassifiedTextItem(
                        text,
                        CaptureCategory.fromValue(node.get("category").asString())
                ));
            }
            return new CaptureAnalysis(items, "gemini", properties.model(), PROMPT_VERSION);
        } catch (CaptureAnalysisException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(
                    "ai_invalid_response",
                    CaptureFailureClass.PROVIDER_RETRYABLE,
                    exception
            );
        }
    }

    private CaptureAnalysisException invalid(String reason) {
        log.warn("Gemini image response rejected: reason={}", reason);
        return failure(
                "ai_invalid_response",
                CaptureFailureClass.PROVIDER_RETRYABLE,
                null
        );
    }

    private CaptureAnalysisException failure(
            String code,
            CaptureFailureClass failureClass,
            Throwable cause
    ) {
        return cause == null
                ? new CaptureAnalysisException(code, "Gemini image analysis failed", failureClass)
                : new CaptureAnalysisException(code, "Gemini image analysis failed", failureClass, cause);
    }

    private record GeminiResponse(List<Candidate> candidates) {
    }

    private record Candidate(Content content, String finishReason) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text) {
    }
}

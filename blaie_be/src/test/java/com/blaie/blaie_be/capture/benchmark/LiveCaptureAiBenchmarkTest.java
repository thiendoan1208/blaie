package com.blaie.blaie_be.capture.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import com.blaie.blaie_be.capture.infrastructure.ai.CapturePromptResources;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in live comparison for prompt and inference profiles.
 *
 * <p>It is intentionally skipped in normal builds. Gemini additionally requires an explicit free-tier
 * acknowledgement and enforces the agreed 12-request/20-second guard before the first network call.
 */
@EnabledIfSystemProperty(named = "blaie.live-ai-benchmark", matches = "true")
class LiveCaptureAiBenchmarkTest {
    private static final int GEMINI_FREE_RPD = 20;
    private static final int GEMINI_RESERVED_RPD = 8;
    private static final int GEMINI_MAX_BENCHMARK_REQUESTS = GEMINI_FREE_RPD - GEMINI_RESERVED_RPD;
    private static final long GEMINI_MIN_INTERVAL_MILLIS = 20_000;
    private static final Path GEMINI_DAILY_LEDGER = Path.of(".ai-benchmark-gemini-usage");
    private static final ZoneId GEMINI_QUOTA_ZONE = ZoneId.of("America/Los_Angeles");

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "items", Map.of(
                            "type", "array",
                            "maxItems", 32,
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "text", Map.of(
                                                    "type", "string",
                                                    "description", "One atomic active record in the user's language."
                                            ),
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CaptureBenchmarkDataset dataset = CaptureBenchmarkDataset.load(objectMapper);

    @Test
    void compareDeepSeekBaselineFastAndReasonedProfiles() {
        assumeProviderEnabled("deepseek");
        String apiKey = requiredSecret("BLAIE_DEEPSEEK_API_KEY");
        String model = setting("blaie.benchmark.deepseek.model", "BLAIE_DEEPSEEK_MODEL", "deepseek-v4-flash");
        int caseLimit = integerSetting("blaie.benchmark.deepseek.cases", dataset.textCases().size());
        int reasonedLimit = integerSetting("blaie.benchmark.deepseek.reasoned-cases", 10);
        assertThat(caseLimit).isBetween(1, dataset.textCases().size());
        assertThat(reasonedLimit).isBetween(0, dataset.textCases().size());

        RestClient client = jsonClient(
                setting("blaie.benchmark.deepseek.base-url", "BLAIE_DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
                Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        );
        List<CaptureBenchmarkDataset.TextCase> cases = dataset.textCases().subList(0, caseLimit);
        DeepSeekProfile baseline = new DeepSeekProfile(
                "baseline-text-v5-fast",
                resource("ai-benchmark/deepseek-text-v5.txt"),
                false,
                "high",
                512
        );
        DeepSeekProfile candidateFast = new DeepSeekProfile(
                "candidate-text-v6-fast",
                CapturePromptResources.deepSeekSystemPrompt(),
                false,
                "high",
                768
        );
        DeepSeekProfile candidateReasoned = new DeepSeekProfile(
                "candidate-text-v6-reasoned",
                CapturePromptResources.deepSeekSystemPrompt(),
                true,
                "high",
                768
        );

        List<CaseRun> baselineRuns = runDeepSeek(client, model, baseline, cases);
        List<CaseRun> fastRuns = runDeepSeek(client, model, candidateFast, cases);
        List<CaptureBenchmarkDataset.TextCase> reasonedCases = failedTextCases(cases, fastRuns)
                .stream()
                .limit(reasonedLimit)
                .toList();
        List<CaseRun> reasonedRuns = runDeepSeek(client, model, candidateReasoned, reasonedCases);

        printReport("deepseek", model, baseline.name(), summarizeText(cases, baselineRuns));
        printReport("deepseek", model, candidateFast.name(), summarizeText(cases, fastRuns));
        if (!reasonedCases.isEmpty()) {
            printReport(
                    "deepseek",
                    model,
                    candidateReasoned.name() + "-failed-subset",
                    summarizeText(reasonedCases, reasonedRuns)
            );
        }

        assertThat(baselineRuns).hasSize(cases.size());
        assertThat(fastRuns).hasSize(cases.size());
    }

    @Test
    void compareGeminiBaselineAndCandidateWithinFreeTierGuard() {
        assumeProviderEnabled("gemini");
        assertThat(Boolean.getBoolean("blaie.benchmark.gemini.confirm-free-tier"))
                .as("set -Dblaie.benchmark.gemini.confirm-free-tier=true after checking today's project usage")
                .isTrue();

        int requestBudget = integerSetting(
                "blaie.benchmark.gemini.max-requests",
                GEMINI_MAX_BENCHMARK_REQUESTS
        );
        long intervalMillis = longSetting(
                "blaie.benchmark.gemini.interval-millis",
                GEMINI_MIN_INTERVAL_MILLIS
        );
        assertThat(requestBudget)
                .as("Gemini benchmark must preserve at least 8 of the 20 daily requests")
                .isBetween(2, GEMINI_MAX_BENCHMARK_REQUESTS);
        assertThat(requestBudget % 2)
                .as("two profiles require an even request budget")
                .isZero();
        assertThat(intervalMillis)
                .as("Gemini benchmark is capped at 3 RPM")
                .isGreaterThanOrEqualTo(GEMINI_MIN_INTERVAL_MILLIS);

        int caseCount = requestBudget / 2;
        assertThat(caseCount).isLessThanOrEqualTo(dataset.imageCases().size());
        int plannedRequests = Math.multiplyExact(caseCount, 2);
        assertThat(plannedRequests).isLessThanOrEqualTo(GEMINI_MAX_BENCHMARK_REQUESTS);
        String candidateThinkingLevel =
                System.getProperty("blaie.benchmark.gemini.candidate-thinking-level", "medium");
        assertThat(candidateThinkingLevel).isIn("minimal", "low", "medium", "high");
        int candidateMaxOutputTokens = integerSetting(
                "blaie.benchmark.gemini.candidate-max-output-tokens",
                2048
        );
        assertThat(candidateMaxOutputTokens).isBetween(64, 8_192);
        reserveGeminiDailyBudget(plannedRequests);

        String apiKey = requiredSecret("BLAIE_GEMINI_API_KEY");
        String model = setting("blaie.benchmark.gemini.model", "BLAIE_GEMINI_MODEL", "gemini-3.6-flash");
        RestClient client = jsonClient(
                setting(
                        "blaie.benchmark.gemini.base-url",
                        "BLAIE_GEMINI_BASE_URL",
                        "https://generativelanguage.googleapis.com/v1beta"
                ),
                Map.of("x-goog-api-key", apiKey)
        );
        GeminiProfile baseline = new GeminiProfile(
                "baseline-image-v1",
                resource("ai-benchmark/gemini-image-v1.txt"),
                null,
                null,
                1024
        );
        GeminiProfile candidate = new GeminiProfile(
                "current-image-v1-" + candidateThinkingLevel + "-high-" + candidateMaxOutputTokens,
                CapturePromptResources.geminiPrompt(),
                candidateThinkingLevel,
                "MEDIA_RESOLUTION_HIGH",
                candidateMaxOutputTokens
        );

        List<CaptureBenchmarkDataset.ImageCase> cases = dataset.imageCases().subList(0, caseCount);
        List<CaseRun> baselineRuns = new ArrayList<>();
        List<CaseRun> candidateRuns = new ArrayList<>();
        RateGate gate = new RateGate(intervalMillis);
        for (CaptureBenchmarkDataset.ImageCase testCase : cases) {
            gate.beforeRequest();
            baselineRuns.add(callGemini(client, model, baseline, testCase));
            gate.beforeRequest();
            candidateRuns.add(callGemini(client, model, candidate, testCase));
        }

        printReport("gemini", model, baseline.name(), summarizeImage(cases, baselineRuns));
        printReport("gemini", model, candidate.name(), summarizeImage(cases, candidateRuns));
        assertThat(baselineRuns).hasSize(caseCount);
        assertThat(candidateRuns).hasSize(caseCount);
    }

    private List<CaseRun> runDeepSeek(
            RestClient client,
            String model,
            DeepSeekProfile profile,
            List<CaptureBenchmarkDataset.TextCase> cases
    ) {
        return cases.stream()
                .map(testCase -> callDeepSeek(client, model, profile, testCase))
                .toList();
    }

    private CaseRun callDeepSeek(
            RestClient client,
            String model,
            DeepSeekProfile profile,
            CaptureBenchmarkDataset.TextCase testCase
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", List.of(
                Map.of("role", "system", "content", profile.prompt()),
                Map.of("role", "user", "content", "<capture>\n" + testCase.input() + "\n</capture>")
        ));
        request.put("response_format", Map.of("type", "json_object"));
        request.put("thinking", Map.of("type", profile.thinking() ? "enabled" : "disabled"));
        if (profile.thinking()) {
            request.put("reasoning_effort", profile.reasoningEffort());
        } else {
            request.put("temperature", 0.0);
        }
        request.put("max_tokens", profile.maxTokens());
        return callProvider(
                testCase.id(),
                () -> client.post()
                        .uri("/chat/completions")
                        .body(request)
                        .retrieve()
                        .body(String.class),
                root -> {
                    String content = root.path("choices").path(0).path("message").path("content").asString();
                    return parseItems(content);
                },
                root -> usage(
                        root.path("usage").path("prompt_tokens").asInt(),
                        root.path("usage").path("completion_tokens").asInt(),
                        root.path("usage").path("reasoning_tokens").asInt(),
                        root.path("usage").path("total_tokens").asInt()
                )
        );
    }

    private CaseRun callGemini(
            RestClient client,
            String model,
            GeminiProfile profile,
            CaptureBenchmarkDataset.ImageCase testCase
    ) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", profile.prompt()));
        if (testCase.optionalText() != null && !testCase.optionalText().isBlank()) {
            parts.add(Map.of("text", testCase.optionalText()));
        }
        parts.add(Map.of("inlineData", Map.of(
                "mimeType", "image/png",
                "data", Base64.getEncoder().encodeToString(renderImage(testCase.visualText()))
        )));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", profile.maxOutputTokens());
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseJsonSchema", RESPONSE_SCHEMA);
        if (profile.thinkingLevel() != null) {
            generationConfig.put("thinkingConfig", Map.of("thinkingLevel", profile.thinkingLevel()));
        } else {
            generationConfig.put("temperature", 0.0);
        }
        if (profile.mediaResolution() != null) {
            generationConfig.put("mediaResolution", profile.mediaResolution());
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        request.put("generationConfig", generationConfig);

        return callProvider(
                testCase.id(),
                () -> client.post()
                        .uri(builder -> builder.path("/models/{model}:generateContent").build(model))
                        .body(request)
                        .retrieve()
                        .body(String.class),
                root -> {
                    String finishReason = root.path("candidates").path(0).path("finishReason").asString();
                    if (!"STOP".equalsIgnoreCase(finishReason)) {
                        throw new IOException("finish-" + finishReason);
                    }
                    String content = root.path("candidates").path(0)
                            .path("content").path("parts").path(0).path("text").asString();
                    if (content.isBlank()) {
                        throw new IOException("empty-content");
                    }
                    return parseItems(content);
                },
                root -> {
                    JsonNode metadata = root.path("usageMetadata");
                    return usage(
                            metadata.path("promptTokenCount").asInt(),
                            metadata.path("candidatesTokenCount").asInt(),
                            metadata.path("thoughtsTokenCount").asInt(),
                            metadata.path("totalTokenCount").asInt()
                    );
                }
        );
    }

    private CaseRun callProvider(
            String caseId,
            ProviderCall providerCall,
            ResponseItems responseItems,
            ResponseUsage responseUsage
    ) {
        long started = System.nanoTime();
        try {
            String response = providerCall.call();
            JsonNode root = objectMapper.readTree(response);
            Usage measuredUsage = responseUsage.read(root);
            List<ClassifiedTextItem> items;
            try {
                items = responseItems.read(root);
            } catch (RuntimeException | IOException exception) {
                return failedRun(caseId, started, measuredUsage, errorLabel(exception));
            }
            return new CaseRun(
                    caseId,
                    items,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    measuredUsage,
                    null
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new IllegalStateException(
                        "Provider quota/rate limit reached; benchmark stopped without further requests",
                        exception
                );
            }
            return failedRun(
                    caseId,
                    started,
                    Usage.ZERO,
                    "http-" + exception.getStatusCode().value()
            );
        } catch (RuntimeException exception) {
            return failedRun(caseId, started, Usage.ZERO, errorLabel(exception));
        }
    }

    private String errorLabel(Exception exception) {
        String detail = exception.getMessage();
        return detail == null || detail.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + "-" + detail;
    }

    private CaseRun failedRun(String caseId, long started, Usage usage, String error) {
        return new CaseRun(
                caseId,
                List.of(),
                Duration.ofNanos(System.nanoTime() - started).toMillis(),
                usage,
                error
        );
    }

    private List<ClassifiedTextItem> parseItems(String content) throws IOException {
        JsonNode root = objectMapper.readTree(content);
        if (!root.isObject() || !root.path("items").isArray()) {
            throw new IOException("invalid response schema");
        }
        List<ClassifiedTextItem> items = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            items.add(new ClassifiedTextItem(
                    item.path("text").asString(),
                    CaptureCategory.fromValue(item.path("category").asString())
            ));
        }
        return items;
    }

    private List<CaptureBenchmarkDataset.TextCase> failedTextCases(
            List<CaptureBenchmarkDataset.TextCase> cases,
            List<CaseRun> runs
    ) {
        List<CaptureBenchmarkDataset.TextCase> failed = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            CaseRun run = runs.get(index);
            if (run.error() != null
                    || !CaptureBenchmarkScorer.score(cases.get(index).expected(), run.items()).exact()) {
                failed.add(cases.get(index));
            }
        }
        return failed;
    }

    private Summary summarizeText(
            List<CaptureBenchmarkDataset.TextCase> cases,
            List<CaseRun> runs
    ) {
        List<List<CaptureBenchmarkDataset.ExpectedItem>> expected = cases.stream()
                .map(CaptureBenchmarkDataset.TextCase::expected)
                .toList();
        return summarize(expected, runs);
    }

    private Summary summarizeImage(
            List<CaptureBenchmarkDataset.ImageCase> cases,
            List<CaseRun> runs
    ) {
        List<List<CaptureBenchmarkDataset.ExpectedItem>> expected = cases.stream()
                .map(CaptureBenchmarkDataset.ImageCase::expected)
                .toList();
        return summarize(expected, runs);
    }

    private Summary summarize(
            List<List<CaptureBenchmarkDataset.ExpectedItem>> expected,
            List<CaseRun> runs
    ) {
        int exact = 0;
        int errors = 0;
        double categoryAccuracy = 0;
        double evidenceRecall = 0;
        long latency = 0;
        Usage usage = Usage.ZERO;
        List<String> failedIds = new ArrayList<>();
        for (int index = 0; index < runs.size(); index++) {
            CaseRun run = runs.get(index);
            var score = run.error() == null
                    ? CaptureBenchmarkScorer.score(expected.get(index), run.items())
                    : new CaptureBenchmarkScorer.CaseScore(false, 0, 0, List.of(run.error()));
            if (score.exact()) {
                exact++;
            } else {
                failedIds.add(run.error() == null
                        ? run.caseId()
                        : run.caseId() + "(" + run.error() + ")");
            }
            if (run.error() != null) {
                errors++;
            }
            categoryAccuracy += score.categoryAccuracy();
            evidenceRecall += score.evidenceRecall();
            latency += run.latencyMillis();
            usage = usage.plus(run.usage());
        }
        int count = runs.size();
        return new Summary(
                count,
                exact,
                errors,
                count == 0 ? 0 : categoryAccuracy / count,
                count == 0 ? 0 : evidenceRecall / count,
                count == 0 ? 0 : latency / count,
                usage,
                failedIds
        );
    }

    private void printReport(String provider, String model, String profile, Summary summary) {
        System.out.printf(
                Locale.ROOT,
                "AI_BENCHMARK provider=%s model=%s profile=%s cases=%d exact=%d errors=%d "
                        + "category=%.3f evidence=%.3f avgLatencyMs=%d inputTokens=%d outputTokens=%d "
                        + "reasoningTokens=%d totalTokens=%d failed=%s%n",
                provider,
                model,
                profile,
                summary.cases(),
                summary.exact(),
                summary.errors(),
                summary.categoryAccuracy(),
                summary.evidenceRecall(),
                summary.averageLatencyMillis(),
                summary.usage().inputTokens(),
                summary.usage().outputTokens(),
                summary.usage().reasoningTokens(),
                summary.usage().totalTokens(),
                summary.failedIds()
        );
    }

    private RestClient jsonClient(String baseUrl, Map<String, String> headers) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.forEach(builder::defaultHeader);
        return builder.build();
    }

    private void assumeProviderEnabled(String provider) {
        String enabled = System.getProperty("blaie.benchmark.provider", "deepseek");
        assumeTrue(
                enabled.equalsIgnoreCase(provider) || enabled.equalsIgnoreCase("both"),
                provider + " live benchmark was not selected"
        );
    }

    private String requiredSecret(String environmentName) {
        String value = System.getenv(environmentName);
        if (value == null || value.isBlank()) {
            value = localDotEnv().get(environmentName);
        }
        assertThat(value)
                .as(environmentName + " must be available through the environment or local .env")
                .isNotBlank();
        return value;
    }

    private String setting(String propertyName, String environmentName, String fallback) {
        String property = System.getProperty(propertyName);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv(environmentName);
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        return localDotEnv().getOrDefault(environmentName, fallback);
    }

    private Map<String, String> localDotEnv() {
        for (Path candidate : List.of(Path.of(".env"), Path.of("..", ".env"))) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                Map<String, String> values = new LinkedHashMap<>();
                for (String line : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    int separator = trimmed.indexOf('=');
                    String key = trimmed.substring(0, separator).trim();
                    String value = trimmed.substring(separator + 1).trim();
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.put(key, value);
                }
                return values;
            } catch (IOException exception) {
                throw new IllegalStateException("Local .env could not be read", exception);
            }
        }
        return Map.of();
    }

    private int integerSetting(String property, int fallback) {
        return Integer.parseInt(System.getProperty(property, Integer.toString(fallback)));
    }

    private long longSetting(String property, long fallback) {
        return Long.parseLong(System.getProperty(property, Long.toString(fallback)));
    }

    private void reserveGeminiDailyBudget(int plannedRequests) {
        LocalDate quotaDate = LocalDate.now(GEMINI_QUOTA_ZONE);
        int alreadyReserved = 0;
        if (Files.isRegularFile(GEMINI_DAILY_LEDGER)) {
            try {
                String[] fields = Files.readString(GEMINI_DAILY_LEDGER, StandardCharsets.UTF_8)
                        .trim()
                        .split("=", 2);
                if (fields.length == 2 && quotaDate.toString().equals(fields[0])) {
                    alreadyReserved = Integer.parseInt(fields[1]);
                }
            } catch (IOException | NumberFormatException exception) {
                throw new IllegalStateException("Gemini benchmark quota ledger is invalid", exception);
            }
        }
        int newTotal = Math.addExact(alreadyReserved, plannedRequests);
        assertThat(newTotal)
                .as("workspace Gemini benchmark reservations for " + quotaDate
                        + " must not exceed " + GEMINI_MAX_BENCHMARK_REQUESTS)
                .isLessThanOrEqualTo(GEMINI_MAX_BENCHMARK_REQUESTS);
        try {
            Files.writeString(
                    GEMINI_DAILY_LEDGER,
                    quotaDate + "=" + newTotal + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Gemini benchmark quota ledger could not be updated", exception);
        }
    }

    private String resource(String path) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing benchmark resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Benchmark resource could not be read: " + path, exception);
        }
    }

    private byte[] renderImage(String text) {
        BufferedImage image = new BufferedImage(1200, 700, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 38));
            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );
            int y = 90;
            for (String line : text.split("\\R")) {
                graphics.drawString(line, 70, y);
                y += 70;
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Synthetic benchmark image could not be rendered", exception);
        }
    }

    private Usage usage(int input, int output, int reasoning, int total) {
        return new Usage(input, output, reasoning, total);
    }

    private record DeepSeekProfile(
            String name,
            String prompt,
            boolean thinking,
            String reasoningEffort,
            int maxTokens
    ) {
    }

    private record GeminiProfile(
            String name,
            String prompt,
            String thinkingLevel,
            String mediaResolution,
            int maxOutputTokens
    ) {
    }

    private record CaseRun(
            String caseId,
            List<ClassifiedTextItem> items,
            long latencyMillis,
            Usage usage,
            String error
    ) {
        CaseRun {
            items = List.copyOf(items);
        }
    }

    private record Usage(int inputTokens, int outputTokens, int reasoningTokens, int totalTokens) {
        private static final Usage ZERO = new Usage(0, 0, 0, 0);

        private Usage plus(Usage other) {
            return new Usage(
                    inputTokens + other.inputTokens,
                    outputTokens + other.outputTokens,
                    reasoningTokens + other.reasoningTokens,
                    totalTokens + other.totalTokens
            );
        }
    }

    private record Summary(
            int cases,
            int exact,
            int errors,
            double categoryAccuracy,
            double evidenceRecall,
            long averageLatencyMillis,
            Usage usage,
            List<String> failedIds
    ) {
        Summary {
            failedIds = List.copyOf(failedIds);
        }
    }

    @FunctionalInterface
    private interface ProviderCall {
        String call();
    }

    @FunctionalInterface
    private interface ResponseItems {
        List<ClassifiedTextItem> read(JsonNode root) throws IOException;
    }

    @FunctionalInterface
    private interface ResponseUsage {
        Usage read(JsonNode root);
    }

    private static final class RateGate {
        private final long intervalMillis;
        private long previousRequestStarted;

        private RateGate(long intervalMillis) {
            this.intervalMillis = intervalMillis;
        }

        private void beforeRequest() {
            long now = System.currentTimeMillis();
            long waitMillis = previousRequestStarted == 0
                    ? 0
                    : intervalMillis - (now - previousRequestStarted);
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Gemini benchmark interrupted", exception);
                }
            }
            previousRequestStarted = System.currentTimeMillis();
        }
    }
}

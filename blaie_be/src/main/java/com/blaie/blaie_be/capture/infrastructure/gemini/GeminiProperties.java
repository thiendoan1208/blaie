package com.blaie.blaie_be.capture.infrastructure.gemini;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.ai.gemini")
public class GeminiProperties {
    private static final Set<String> THINKING_LEVELS = Set.of("minimal", "low", "medium", "high");
    private static final Set<String> MEDIA_RESOLUTIONS = Set.of("low", "medium", "high");

    private String apiKey;
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-3.6-flash";
    private Duration timeout = Duration.ofSeconds(15);
    private String thinkingLevel = "medium";
    private String mediaResolution = "high";
    private int maxOutputTokens = 2048;

    public String apiKey() {
        return apiKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public Duration timeout() {
        return timeout;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String thinkingLevel() {
        return thinkingLevel;
    }

    public void setThinkingLevel(String thinkingLevel) {
        this.thinkingLevel = thinkingLevel;
    }

    public String mediaResolution() {
        return mediaResolution;
    }

    public void setMediaResolution(String mediaResolution) {
        this.mediaResolution = mediaResolution;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    @AssertTrue(message = "Gemini AI configuration is invalid")
    public boolean isConfigurationValid() {
        return hasText(baseUrl)
                && hasText(model)
                && timeout != null
                && !timeout.isNegative()
                && !timeout.isZero()
                && normalizedIn(thinkingLevel, THINKING_LEVELS)
                && normalizedIn(mediaResolution, MEDIA_RESOLUTIONS)
                && maxOutputTokens >= 64
                && maxOutputTokens <= 8_192;
    }

    private boolean normalizedIn(String value, Set<String> allowed) {
        return value != null && allowed.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

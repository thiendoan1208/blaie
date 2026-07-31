package com.blaie.blaie_be.capture.infrastructure.deepseek;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.ai.deepseek")
public class DeepSeekProperties {
    private static final Set<String> REASONING_EFFORTS = Set.of("high", "max");

    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-v4-flash";
    private Duration timeout = Duration.ofSeconds(8);
    private boolean thinkingEnabled;
    private String reasoningEffort = "high";
    private double temperature;
    private int maxTokens = 768;

    public String apiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String baseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String model() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration timeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean thinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
    }

    public String reasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public double temperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @AssertTrue(message = "DeepSeek AI configuration is invalid")
    public boolean isConfigurationValid() {
        return hasText(baseUrl)
                && hasText(model)
                && timeout != null
                && !timeout.isNegative()
                && !timeout.isZero()
                && reasoningEffort != null
                && REASONING_EFFORTS.contains(reasoningEffort.trim().toLowerCase(Locale.ROOT))
                && temperature >= 0.0
                && temperature <= 2.0
                && maxTokens >= 64
                && maxTokens <= 8_192;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

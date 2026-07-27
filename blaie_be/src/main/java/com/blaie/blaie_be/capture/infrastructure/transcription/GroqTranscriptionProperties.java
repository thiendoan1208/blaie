package com.blaie.blaie_be.capture.infrastructure.transcription;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blaie.transcription.groq")
public class GroqTranscriptionProperties {
    private String apiKey = "";
    private String baseUrl = "https://api.groq.com/openai/v1";
    private String model = "whisper-large-v3-turbo";
    private Duration timeout = Duration.ofSeconds(20);

    public String apiKey() {
        return apiKey;
    }

    public String baseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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

}

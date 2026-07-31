package com.blaie.blaie_be.capture.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GeminiPropertiesTest {
    @Test
    void defaultsAreValidAndUseDocumentedGeminiValues() {
        GeminiProperties properties = new GeminiProperties();

        assertThat(properties.thinkingLevel()).isEqualTo("medium");
        assertThat(properties.mediaResolution()).isEqualTo("high");
        assertThat(properties.maxOutputTokens()).isEqualTo(2048);
        assertThat(properties.isConfigurationValid()).isTrue();
    }

    @Test
    void rejectsInvalidThinkingMediaAndOutputConfiguration() {
        GeminiProperties properties = new GeminiProperties();

        properties.setThinkingLevel("ultra");
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setThinkingLevel("low");
        properties.setMediaResolution("original");
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setMediaResolution("medium");
        properties.setMaxOutputTokens(32);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setMaxOutputTokens(2048);
        properties.setTimeout(Duration.ZERO);
        assertThat(properties.isConfigurationValid()).isFalse();
    }
}

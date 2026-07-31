package com.blaie.blaie_be.capture.infrastructure.deepseek;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeepSeekPropertiesTest {
    @Test
    void defaultsAreValidAndMatchMeasuredFastProfile() {
        DeepSeekProperties properties = new DeepSeekProperties();

        assertThat(properties.thinkingEnabled()).isFalse();
        assertThat(properties.reasoningEffort()).isEqualTo("high");
        assertThat(properties.temperature()).isZero();
        assertThat(properties.maxTokens()).isEqualTo(768);
        assertThat(properties.isConfigurationValid()).isTrue();
    }

    @Test
    void rejectsProviderValuesThatWouldFailAtRuntime() {
        DeepSeekProperties properties = new DeepSeekProperties();

        properties.setReasoningEffort("medium");
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setReasoningEffort("max");
        properties.setTemperature(2.1);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setTemperature(0.0);
        properties.setTimeout(Duration.ZERO);
        assertThat(properties.isConfigurationValid()).isFalse();
    }
}

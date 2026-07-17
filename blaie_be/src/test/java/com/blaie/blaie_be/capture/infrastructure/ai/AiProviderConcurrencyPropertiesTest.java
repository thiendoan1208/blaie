package com.blaie.blaie_be.capture.infrastructure.ai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderConcurrencyPropertiesTest {

    @Test
    void defaultsAreConservativeAndProviderSpecificLimitsOverrideThem() {
        AiProviderConcurrencyProperties properties = new AiProviderConcurrencyProperties();
        properties.setProviderLimits(Map.of("DeepSeek", 3));

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.defaultLimit()).isEqualTo(1);
        assertThat(properties.limitFor("deepseek")).isEqualTo(3);
        assertThat(properties.limitFor("openai")).isEqualTo(1);
        assertThat(properties.leaseDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.renewalInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.renewalPoolSize()).isEqualTo(1);
        assertThat(properties.pollInterval()).isEqualTo(Duration.ofMillis(100));
        assertThat(properties.keyFor("DeepSeek")).isEqualTo("blaie:ai:concurrency:deepseek");
        assertThat(properties.isConfigurationValid()).isTrue();
    }

    @Test
    void rejectsInvalidLimitsDurationsAndAmbiguousProviderIds() {
        AiProviderConcurrencyProperties properties = new AiProviderConcurrencyProperties();
        properties.setDefaultLimit(0);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setDefaultLimit(1);
        properties.setPollInterval(properties.leaseDuration());
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setPollInterval(Duration.ofMillis(100));
        properties.setRenewalInterval(Duration.ofSeconds(11));
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setRenewalInterval(Duration.ofSeconds(10));
        properties.setRenewalPoolSize(0);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setRenewalPoolSize(1);
        LinkedHashMap<String, Integer> duplicateIds = new LinkedHashMap<>();
        duplicateIds.put("DeepSeek", 1);
        duplicateIds.put("deepseek", 2);
        properties.setProviderLimits(duplicateIds);
        assertThat(properties.isConfigurationValid()).isFalse();

        properties.setProviderLimits(Map.of("bad:provider", 1));
        assertThat(properties.isConfigurationValid()).isFalse();
    }

    @Test
    void rejectsProviderIdsThatCouldEscapeTheRedisKeyNamespace() {
        AiProviderConcurrencyProperties properties = new AiProviderConcurrencyProperties();

        assertThatThrownBy(() -> properties.keyFor("deepseek:other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid AI provider id");
    }
}

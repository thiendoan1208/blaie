package com.blaie.blaie_be.capture.infrastructure.ai;

import com.blaie.blaie_be.capture.application.port.TextClassifierProvider;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderRouterTest {
    @Test
    void retryablePrimaryFailureFallsBackToNextConfiguredProvider() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setProvider("primary");
        properties.setFallbackProviders(List.of("fallback"));
        CaptureAnalysis expected = new CaptureAnalysis(List.of(), "fallback", "model", "v1");
        AiProviderRouter router = new AiProviderRouter(List.of(
                provider("primary", text -> {
                    throw new TextClassificationException("ai_provider_unavailable", "down", true);
                }),
                provider("fallback", text -> expected)
        ), properties);

        assertThat(router.classify("Buy milk")).isSameAs(expected);
    }

    private TextClassifierProvider provider(
            String id,
            java.util.function.Function<String, CaptureAnalysis> classifier
    ) {
        return new TextClassifierProvider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public CaptureAnalysis classify(String text) {
                return classifier.apply(text);
            }
        };
    }
}

package com.blaie.blaie_be.capture.infrastructure.ai;

import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.port.TextClassifierProvider;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiProviderRouter implements TextClassifierPort {
    private static final Logger log = LoggerFactory.getLogger(AiProviderRouter.class);
    private final Map<String, TextClassifierProvider> providers;
    private final AiProviderProperties properties;

    public AiProviderRouter(List<TextClassifierProvider> providers, AiProviderProperties properties) {
        Map<String, TextClassifierProvider> indexed = new LinkedHashMap<>();
        for (TextClassifierProvider provider : providers) {
            String providerId = normalize(provider.providerId());
            if (indexed.putIfAbsent(providerId, provider) != null) {
                throw new IllegalStateException("Duplicate AI provider id: " + providerId);
            }
        }
        this.providers = Map.copyOf(indexed);
        this.properties = properties;
    }

    @Override
    public CaptureAnalysis classify(String text) {
        TextClassificationException lastRetryableFailure = null;
        for (String providerId : route()) {
            TextClassifierProvider provider = providers.get(providerId);
            if (provider == null) {
                continue;
            }
            try {
                return provider.classify(text);
            } catch (TextClassificationException exception) {
                if (!exception.retryable()) {
                    throw exception;
                }
                log.warn(
                        "AI text provider attempt failed: provider={}, errorCode={}, retryable={}",
                        providerId,
                        exception.failureCode(),
                        true
                );
                lastRetryableFailure = exception;
            }
        }
        if (lastRetryableFailure != null) {
            throw lastRetryableFailure;
        }
        throw new TextClassificationException(
                "ai_provider_not_configured",
                "No configured AI provider is available",
                false
        );
    }

    private List<String> route() {
        List<String> route = new ArrayList<>();
        route.add(normalize(properties.provider()));
        properties.fallbackProviders().stream()
                .map(this::normalize)
                .filter(provider -> !route.contains(provider))
                .forEach(route::add);
        return route;
    }

    private String normalize(String providerId) {
        return providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
    }
}

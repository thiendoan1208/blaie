package com.blaie.blaie_be.capture.infrastructure.ai;

import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.port.TextClassifierProvider;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
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
    private final ProviderConcurrencyLimiter concurrencyLimiter;

    public AiProviderRouter(
            List<TextClassifierProvider> providers,
            AiProviderProperties properties,
            ProviderConcurrencyLimiter concurrencyLimiter
    ) {
        Map<String, TextClassifierProvider> indexed = new LinkedHashMap<>();
        for (TextClassifierProvider provider : providers) {
            String providerId = normalize(provider.providerId());
            if (indexed.putIfAbsent(providerId, provider) != null) {
                throw new IllegalStateException("Duplicate AI provider id: " + providerId);
            }
        }
        this.providers = Map.copyOf(indexed);
        this.properties = properties;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    @Override
    public CaptureAnalysis classify(String text) {
        TextClassificationException lastProviderRetryableFailure = null;
        TextClassificationException lastProviderTerminalFailure = null;
        for (String providerId : route()) {
            TextClassifierProvider provider = providers.get(providerId);
            if (provider == null) {
                lastProviderTerminalFailure = providerNotConfigured(providerId);
                continue;
            }
            try (ProviderConcurrencyLimiter.Permit ignored = concurrencyLimiter.acquire(providerId)) {
                return provider.classify(text);
            } catch (TextClassificationException exception) {
                TextClassificationFailureClass failureClass = exception.failureClass();
                if (!failureClass.providerFallbackAllowed()) {
                    throw exception;
                }
                log.warn(
                        "AI text provider attempt failed: provider={}, errorCode={}, failureClass={}",
                        providerId,
                        exception.failureCode(),
                        failureClass.value()
                );
                if (failureClass == TextClassificationFailureClass.PROVIDER_RETRYABLE) {
                    lastProviderRetryableFailure = exception;
                } else {
                    lastProviderTerminalFailure = exception;
                }
            }
        }
        if (lastProviderRetryableFailure != null) {
            throw lastProviderRetryableFailure;
        }
        if (lastProviderTerminalFailure != null) {
            throw lastProviderTerminalFailure;
        }
        throw providerNotConfigured("");
    }

    private TextClassificationException providerNotConfigured(String providerId) {
        String message = providerId.isBlank()
                ? "No AI provider is configured"
                : "AI provider is not configured: " + providerId;
        return new TextClassificationException(
                "ai_provider_not_configured",
                message,
                TextClassificationFailureClass.PROVIDER_TERMINAL
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

package com.blaie.blaie_be.capture.infrastructure.ai;

import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.ProviderOutcome;
import com.blaie.blaie_be.capture.application.port.TextClassifierPort;
import com.blaie.blaie_be.capture.application.port.TextClassifierProvider;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import com.blaie.blaie_be.core.request.MdcContextScope;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiProviderRouter implements TextClassifierPort {
    private static final Logger log = LoggerFactory.getLogger(AiProviderRouter.class);
    private final Map<String, TextClassifierProvider> providers;
    private final AiProviderProperties properties;
    private final ProviderConcurrencyLimiter concurrencyLimiter;
    private final CaptureTelemetryPort telemetry;

    public AiProviderRouter(
            List<TextClassifierProvider> providers,
            AiProviderProperties properties,
            ProviderConcurrencyLimiter concurrencyLimiter,
            CaptureTelemetryPort telemetry
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
        this.telemetry = telemetry;
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
            try (MdcContextScope ignored = MdcContextScope.overlay(Map.of(
                    "provider", providerId,
                    "providerAttemptId", UUID.randomUUID().toString()
            ))) {
                try (ProviderConcurrencyLimiter.Permit permit = concurrencyLimiter.acquire(providerId)) {
                    return classifyWithProviderMetrics(provider, providerId, text);
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
        }
        if (lastProviderRetryableFailure != null) {
            throw lastProviderRetryableFailure;
        }
        if (lastProviderTerminalFailure != null) {
            throw lastProviderTerminalFailure;
        }
        throw providerNotConfigured("");
    }

    private CaptureAnalysis classifyWithProviderMetrics(
            TextClassifierProvider provider,
            String providerId,
            String text
    ) {
        long startedAtNanos = System.nanoTime();
        try {
            CaptureAnalysis analysis = provider.classify(text);
            telemetry.recordProviderDuration(
                    elapsed(startedAtNanos),
                    providerId,
                    ProviderOutcome.SUCCESS
            );
            return analysis;
        } catch (TextClassificationException exception) {
            telemetry.recordProviderDuration(
                    elapsed(startedAtNanos),
                    providerId,
                    ProviderOutcome.FAILURE
            );
            telemetry.incrementProviderError(providerId, safeFailureClass(exception.failureClass()));
            throw exception;
        } catch (RuntimeException exception) {
            telemetry.recordProviderDuration(
                    elapsed(startedAtNanos),
                    providerId,
                    ProviderOutcome.FAILURE
            );
            telemetry.incrementProviderError(providerId, TextClassificationFailureClass.SYSTEM_RETRYABLE);
            throw exception;
        }
    }

    private TextClassificationFailureClass safeFailureClass(TextClassificationFailureClass failureClass) {
        return failureClass == null
                ? TextClassificationFailureClass.SYSTEM_RETRYABLE
                : failureClass;
    }

    private Duration elapsed(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
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

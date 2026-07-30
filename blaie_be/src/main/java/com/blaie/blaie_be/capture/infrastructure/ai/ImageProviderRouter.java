package com.blaie.blaie_be.capture.infrastructure.ai;

import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.ProviderOutcome;
import com.blaie.blaie_be.capture.application.port.ImageAnalysisInput;
import com.blaie.blaie_be.capture.application.port.ImageAnalyzerPort;
import com.blaie.blaie_be.capture.application.port.ImageAnalyzerProvider;
import com.blaie.blaie_be.capture.domain.CaptureAnalysis;
import com.blaie.blaie_be.capture.domain.TextClassificationException;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import com.blaie.blaie_be.core.request.MdcContextScope;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

@Component
@Primary
public class ImageProviderRouter implements ImageAnalyzerPort {
    private final Map<String, ImageAnalyzerProvider> providers;
    private final ImageAiProperties properties;
    private final ProviderConcurrencyLimiter concurrencyLimiter;
    private final CaptureTelemetryPort telemetry;

    public ImageProviderRouter(
            List<ImageAnalyzerProvider> providers,
            ImageAiProperties properties,
            ProviderConcurrencyLimiter concurrencyLimiter,
            CaptureTelemetryPort telemetry
    ) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> normalize(provider.providerId()),
                Function.identity()
        ));
        this.properties = properties;
        this.concurrencyLimiter = concurrencyLimiter;
        this.telemetry = telemetry;
    }

    @Override
    public CaptureAnalysis analyze(ImageAnalysisInput input) {
        String providerId = normalize(properties.provider());
        ImageAnalyzerProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new TextClassificationException(
                    "ai_provider_not_configured",
                    "Image AI provider is not configured",
                    TextClassificationFailureClass.PROVIDER_TERMINAL
            );
        }
        long startedAt = System.nanoTime();
        try (MdcContextScope _ = MdcContextScope.overlay(Map.of(
                "provider", providerId,
                "providerAttemptId", UUID.randomUUID().toString()
        )); ProviderConcurrencyLimiter.Permit _ = concurrencyLimiter.acquire(providerId)) {
            CaptureAnalysis analysis = provider.analyze(input);
            telemetry.recordProviderDuration(elapsed(startedAt), providerId, ProviderOutcome.SUCCESS);
            return analysis;
        } catch (TextClassificationException exception) {
            telemetry.recordProviderDuration(elapsed(startedAt), providerId, ProviderOutcome.FAILURE);
            telemetry.incrementProviderError(providerId, exception.failureClass());
            throw exception;
        }
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

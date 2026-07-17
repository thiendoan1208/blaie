package com.blaie.blaie_be.capture.infrastructure.observability;

import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MicrometerCaptureTelemetry implements CaptureTelemetryPort {
    private static final Logger log = LoggerFactory.getLogger(MicrometerCaptureTelemetry.class);
    private static final String UNKNOWN_PROVIDER = "unknown";
    private static final String PROVIDER_ID_PATTERN = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}";

    private final MeterRegistry registry;

    public MicrometerCaptureTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordJobDuration(Duration duration, JobOutcome outcome) {
        safely("capture.job.duration", () -> Timer.builder("capture.job.duration")
                .description("End-to-end duration of a claimed capture job execution")
                .tag("outcome", outcome.value())
                .register(registry)
                .record(nonNegative(duration)));
    }

    @Override
    public void recordProviderDuration(
            Duration duration,
            String providerId,
            ProviderOutcome outcome
    ) {
        safely("capture.provider.duration", () -> Timer.builder("capture.provider.duration")
                .description("Duration of a single AI provider call, excluding concurrency wait")
                .tag("provider", providerTag(providerId))
                .tag("outcome", outcome.value())
                .register(registry)
                .record(nonNegative(duration)));
    }

    @Override
    public void incrementProviderError(
            String providerId,
            TextClassificationFailureClass failureClass
    ) {
        safely("capture.provider.errors", () -> Counter.builder("capture.provider.errors")
                .description("AI provider attempt failures grouped by bounded failure class")
                .tag("provider", providerTag(providerId))
                .tag("failure_class", failureClass.value())
                .register(registry)
                .increment());
    }

    @Override
    public void incrementRetry(RetrySource source) {
        safely("capture.retry", () -> Counter.builder("capture.retry")
                .description("Capture job retries")
                .tag("source", source.value())
                .register(registry)
                .increment());
    }

    @Override
    public void incrementDead(
            DeadSource source,
            TextClassificationFailureClass failureClass
    ) {
        safely("capture.dead", () -> Counter.builder("capture.dead")
                .description("Capture jobs that entered the dead state")
                .tag("source", source.value())
                .tag("failure_class", failureClass.value())
                .register(registry)
                .increment());
    }

    @Override
    public void incrementStaleRecovered(long count) {
        incrementCounter("capture.stale.recovered", "Stale capture leases recovered", count);
    }

    @Override
    public void incrementQueuedRedispatched(long count) {
        incrementCounter("capture.queued.redispatched", "Queued capture jobs redispatched", count);
    }

    @Override
    public void recordProviderConcurrencyWait(
            Duration duration,
            String providerId,
            ConcurrencyWaitOutcome outcome
    ) {
        safely("capture.provider.concurrency.wait", () -> Timer.builder("capture.provider.concurrency.wait")
                .description("Time spent waiting for a distributed AI provider permit")
                .tag("provider", providerTag(providerId))
                .tag("outcome", outcome.value())
                .register(registry)
                .record(nonNegative(duration)));
    }

    private void incrementCounter(String name, String description, long count) {
        if (count <= 0) {
            return;
        }
        safely(name, () -> Counter.builder(name)
                .description(description)
                .register(registry)
                .increment(count));
    }

    private void safely(String meterName, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            log.debug("Capture telemetry meter could not be recorded: meter={}", meterName, exception);
        }
    }

    private Duration nonNegative(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return Duration.ZERO;
        }
        return duration;
    }

    private String providerTag(String providerId) {
        if (providerId == null || !providerId.matches(PROVIDER_ID_PATTERN)) {
            return UNKNOWN_PROVIDER;
        }
        return providerId.toLowerCase(Locale.ROOT);
    }
}

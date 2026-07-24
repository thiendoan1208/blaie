package com.blaie.blaie_be.capture.infrastructure.observability;

import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.ConcurrencyWaitOutcome;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.DeadSource;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.JobOutcome;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.ProviderOutcome;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.RetrySource;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class MicrometerCaptureTelemetryTest {
    @Test
    void recordsBoundedJobAndProviderMetersWithoutResourceIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerCaptureTelemetry telemetry = new MicrometerCaptureTelemetry(registry);

        telemetry.recordJobDuration(Duration.ofSeconds(2), JobOutcome.COMPLETED);
        telemetry.recordProviderDuration(Duration.ofMillis(750), "DeepSeek", ProviderOutcome.FAILURE);
        telemetry.incrementProviderError(
                "DeepSeek",
                TextClassificationFailureClass.PROVIDER_RETRYABLE
        );
        telemetry.incrementRetry(RetrySource.AUTOMATIC);
        telemetry.incrementRetry(RetrySource.ADMIN);
        telemetry.incrementDead(DeadSource.WORKER, TextClassificationFailureClass.SYSTEM_RETRYABLE);
        telemetry.incrementStaleRecovered(2);
        telemetry.incrementQueuedRedispatched(3);
        telemetry.recordProviderConcurrencyWait(
                Duration.ofMillis(125),
                "DeepSeek",
                ConcurrencyWaitOutcome.ACQUIRED
        );

        assertThat(registry.get("capture.job.duration")
                .tag("outcome", "completed").timer().count()).isEqualTo(1);
        assertThat(registry.get("capture.provider.duration")
                .tags("provider", "deepseek", "outcome", "failure").timer().count()).isEqualTo(1);
        assertThat(registry.get("capture.provider.errors")
                .tags("provider", "deepseek", "failure_class", "provider_retryable")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("capture.retry").tag("source", "automatic").counter().count()).isEqualTo(1);
        assertThat(registry.get("capture.retry").tag("source", "admin").counter().count()).isEqualTo(1);
        assertThat(registry.get("capture.dead")
                .tags("source", "worker", "failure_class", "system_retryable")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("capture.stale.recovered").counter().count()).isEqualTo(2);
        assertThat(registry.get("capture.queued.redispatched").counter().count()).isEqualTo(3);
        assertThat(registry.get("capture.provider.concurrency.wait")
                .tags("provider", "deepseek", "outcome", "acquired").timer().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTag("userId")).isNull();
            assertThat(meter.getId().getTag("captureId")).isNull();
            assertThat(meter.getId().getTag("jobId")).isNull();
            assertThat(meter.getId().getTag("requestId")).isNull();
        });
    }

    @Test
    void normalizesInvalidProviderAndIgnoresNonPositiveBulkCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerCaptureTelemetry telemetry = new MicrometerCaptureTelemetry(registry);

        telemetry.recordProviderDuration(Duration.ofMillis(-1), "unsafe provider/id", ProviderOutcome.SUCCESS);
        telemetry.incrementStaleRecovered(0);
        telemetry.incrementQueuedRedispatched(-1);

        assertThat(registry.get("capture.provider.duration")
                .tags("provider", "unknown", "outcome", "success").timer().totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
                .isZero();
        assertThat(registry.find("capture.stale.recovered").counter()).isNull();
        assertThat(registry.find("capture.queued.redispatched").counter()).isNull();
    }

    @Test
    void telemetryBackendFailuresNeverEscapeIntoCaptureProcessing() {
        MicrometerCaptureTelemetry telemetry = new MicrometerCaptureTelemetry(mock(MeterRegistry.class));

        assertThatCode(() -> {
            telemetry.recordJobDuration(Duration.ofSeconds(1), JobOutcome.COMPLETED);
            telemetry.recordProviderDuration(Duration.ofSeconds(1), "deepseek", ProviderOutcome.SUCCESS);
            telemetry.incrementProviderError(
                    "deepseek",
                    TextClassificationFailureClass.PROVIDER_RETRYABLE
            );
            telemetry.incrementRetry(RetrySource.AUTOMATIC);
            telemetry.incrementDead(DeadSource.WORKER, TextClassificationFailureClass.SYSTEM_RETRYABLE);
            telemetry.incrementStaleRecovered(1);
            telemetry.incrementQueuedRedispatched(1);
            telemetry.recordProviderConcurrencyWait(
                    Duration.ofMillis(1),
                    "deepseek",
                    ConcurrencyWaitOutcome.ACQUIRED
            );
        }).doesNotThrowAnyException();
    }
}

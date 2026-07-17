package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.ConcurrencyWaitOutcome;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.DeadSource;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.JobOutcome;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.ProviderOutcome;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.RetrySource;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import com.blaie.blaie_be.capture.infrastructure.observability.CaptureOperationalMetricsCollector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.server.address=127.0.0.1",
                "blaie.capture.observability.collector-enabled=true",
                "blaie.capture.observability.snapshot-interval=1h",
                "blaie.capture.processing.accept-async-enabled=false",
                "blaie.capture.processing.publisher-enabled=false",
                "blaie.capture.processing.worker-enabled=false",
                "blaie.capture.processing.recovery-enabled=false",
                "blaie.auth.access-token-secret=management-test-access-secret-at-least-32-bytes",
                "blaie.email.provider=log",
                "blaie.email.from=Blaie <no-reply@test.local>",
                "blaie.email.web-base-url=http://localhost:3000",
                "blaie.email.api-base-url=http://localhost:8080/api/v1",
                "blaie.email.verification-ttl=24h",
                "blaie.google.oauth.client-id=test-google-client-id",
                "blaie.google.oauth.client-secret=test-google-client-secret",
                "blaie.google.oauth.redirect-uri=http://localhost:8080/api/v1/auth/google/callback",
                "blaie.google.oauth.web-base-url=http://localhost:3000"
        }
)
class ManagementEndpointSecurityIntegrationTest {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private CaptureTelemetryPort telemetry;

    @Autowired
    private CaptureOperationalMetricsCollector operationalMetricsCollector;

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @BeforeEach
    void publishLazyCaptureMetersAndRefreshOperationalGauges() {
        telemetry.recordJobDuration(Duration.ofMillis(5), JobOutcome.COMPLETED);
        telemetry.recordProviderDuration(Duration.ofMillis(5), "deepseek", ProviderOutcome.FAILURE);
        telemetry.incrementProviderError(
                "deepseek",
                TextClassificationFailureClass.PROVIDER_RETRYABLE
        );
        telemetry.incrementRetry(RetrySource.AUTOMATIC);
        telemetry.incrementDead(
                DeadSource.WORKER,
                TextClassificationFailureClass.SYSTEM_RETRYABLE
        );
        telemetry.incrementStaleRecovered(1);
        telemetry.incrementQueuedRedispatched(1);
        telemetry.recordProviderConcurrencyWait(
                Duration.ofMillis(5),
                "deepseek",
                ConcurrencyWaitOutcome.ACQUIRED
        );
        operationalMetricsCollector.refresh();
    }

    @Test
    void onlyTheLoopbackManagementPortExposesHealthAndPrometheusWithoutCredentials() throws Exception {
        HttpResponse<String> prometheus = get(managementPort, "/actuator/prometheus");
        HttpResponse<String> liveness = get(managementPort, "/actuator/health/liveness");
        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");
        HttpResponse<String> unexposedEnvironment = get(managementPort, "/actuator/env");
        HttpResponse<String> applicationPrometheus = get(applicationPort, "/actuator/prometheus");

        assertThat(prometheus.statusCode()).isEqualTo(200);
        assertThat(prometheus.body()).contains("# HELP", "jvm_");
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(unexposedEnvironment.statusCode()).isNotEqualTo(200);
        assertThat(applicationPrometheus.statusCode()).isNotEqualTo(200);
        assertThat(applicationPrometheus.body()).doesNotContain("# HELP", "jvm_");
    }

    @Test
    void prometheusScrapeExportsTheExactCaptureMetricContractUsedByOperationsArtifacts() throws Exception {
        HttpResponse<String> prometheus = get(managementPort, "/actuator/prometheus");

        assertThat(prometheus.statusCode()).isEqualTo(200);
        assertThat(sampleNames(prometheus.body())).contains(
                "capture_queue_depth",
                "capture_oldest_queued_age_seconds",
                "capture_active_leases",
                "capture_job_duration_seconds_bucket",
                "capture_job_duration_seconds_count",
                "capture_job_duration_seconds_sum",
                "capture_provider_duration_seconds_bucket",
                "capture_provider_duration_seconds_count",
                "capture_provider_duration_seconds_sum",
                "capture_provider_errors_total",
                "capture_retry_total",
                "capture_dead_total",
                "capture_stale_recovered_total",
                "capture_queued_redispatched_total",
                "capture_provider_concurrency_wait_seconds_bucket",
                "capture_provider_concurrency_wait_seconds_count",
                "capture_provider_concurrency_wait_seconds_sum",
                "capture_provider_concurrency_usage",
                "capture_provider_concurrency_limit",
                "capture_outbox_backlog",
                "capture_outbox_oldest_age_seconds",
                "capture_redis_stream_pending",
                "capture_redis_stream_length",
                "capture_observability_source_up",
                "capture_observability_source_last_success_seconds"
        );
        assertThat(samples(prometheus.body(), "capture_queue_depth"))
                .anyMatch(line -> line.contains("state=\"queued\""))
                .anyMatch(line -> line.contains("state=\"retry_wait\""))
                .anyMatch(line -> line.contains("state=\"processing\""));
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private Set<String> sampleNames(String prometheusBody) {
        return prometheusBody.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(this::sampleName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<String> samples(String prometheusBody, String metricName) {
        return prometheusBody.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(metricName + "{") || line.startsWith(metricName + " "))
                .toList();
    }

    private String sampleName(String sampleLine) {
        int labelsStart = sampleLine.indexOf('{');
        int valueStart = sampleLine.indexOf(' ');
        int nameEnd = labelsStart >= 0 ? labelsStart : valueStart;
        return nameEnd >= 0 ? sampleLine.substring(0, nameEnd) : sampleLine;
    }
}

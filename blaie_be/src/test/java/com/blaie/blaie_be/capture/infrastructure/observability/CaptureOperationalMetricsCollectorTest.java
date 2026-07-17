package com.blaie.blaie_be.capture.infrastructure.observability;

import com.blaie.blaie_be.capture.infrastructure.ai.AiProviderConcurrencyProperties;
import com.blaie.blaie_be.capture.infrastructure.ai.AiProviderProperties;
import com.blaie.blaie_be.capture.infrastructure.async.CaptureProcessingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class CaptureOperationalMetricsCollectorTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void refreshesDatabaseRedisAndProviderSnapshotsIntoConstantTimeGauges() {
        Fixture fixture = fixture();
        when(fixture.reader.readJobs()).thenReturn(new CaptureOperationalSnapshotReader.JobSnapshot(
                4,
                2,
                3,
                1,
                NOW.minusSeconds(45)
        ));
        when(fixture.reader.readOutbox()).thenReturn(new CaptureOperationalSnapshotReader.OutboxSnapshot(
                3,
                NOW.minusSeconds(20)
        ));
        when(fixture.streams.size("capture-stream")).thenReturn(9L);
        PendingMessagesSummary pending = mock(PendingMessagesSummary.class);
        when(pending.getTotalPendingMessages()).thenReturn(5L);
        when(fixture.streams.pending("capture-stream", "capture-group")).thenReturn(pending);
        when(fixture.redis.execute(
                eq(CaptureOperationalMetricsCollector.PROVIDER_USAGE),
                eq(List.of("test:ai:deepseek"))
        )).thenReturn(1L);

        fixture.collector.refresh();

        assertGauge(fixture.registry, "capture.queue.depth", "state", "queued", 4);
        assertGauge(fixture.registry, "capture.queue.depth", "state", "retry_wait", 2);
        assertGauge(fixture.registry, "capture.queue.depth", "state", "processing", 3);
        assertThat(fixture.registry.get("capture.oldest.queued.age")
                .timeGauge().value(TimeUnit.SECONDS)).isEqualTo(45);
        assertGauge(fixture.registry, "capture.active.leases", 1);
        assertGauge(fixture.registry, "capture.outbox.backlog", 3);
        assertThat(fixture.registry.get("capture.outbox.oldest.age")
                .timeGauge().value(TimeUnit.SECONDS)).isEqualTo(20);
        assertGauge(fixture.registry, "capture.redis.stream.pending", 5);
        assertGauge(fixture.registry, "capture.redis.stream.length", 9);
        assertGauge(fixture.registry, "capture.provider.concurrency.usage", "provider", "deepseek", 1);
        assertGauge(fixture.registry, "capture.provider.concurrency.limit", "provider", "deepseek", 2);
        assertGauge(fixture.registry, "capture.observability.source.up", "source", "db", 1);
        assertGauge(fixture.registry, "capture.observability.source.up", "source", "redis", 1);
        assertGauge(
                fixture.registry,
                "capture.observability.source.last.success",
                "source",
                "db",
                NOW.getEpochSecond()
        );
    }

    @Test
    void sourceFailuresAreVisibleAndDoNotDiscardLastGoodValues() {
        Fixture fixture = fixture();
        when(fixture.reader.readJobs()).thenReturn(new CaptureOperationalSnapshotReader.JobSnapshot(
                2, 0, 1, 0, NOW.minusSeconds(10)
        ));
        when(fixture.reader.readOutbox()).thenReturn(new CaptureOperationalSnapshotReader.OutboxSnapshot(0, null));
        when(fixture.streams.size("capture-stream")).thenReturn(1L);
        when(fixture.streams.pending("capture-stream", "capture-group")).thenReturn(null);
        when(fixture.redis.execute(
                eq(CaptureOperationalMetricsCollector.PROVIDER_USAGE),
                eq(List.of("test:ai:deepseek"))
        )).thenReturn(0L);
        fixture.collector.refresh();

        when(fixture.reader.readJobs()).thenThrow(new IllegalStateException("database unavailable"));
        when(fixture.streams.size("capture-stream")).thenThrow(new IllegalStateException("redis unavailable"));

        fixture.collector.refresh();

        assertGauge(fixture.registry, "capture.observability.source.up", "source", "db", 0);
        assertGauge(fixture.registry, "capture.observability.source.up", "source", "redis", 0);
        assertGauge(fixture.registry, "capture.queue.depth", "state", "queued", 2);
        assertGauge(fixture.registry, "capture.queue.depth", "state", "processing", 1);
        assertGauge(fixture.registry, "capture.redis.stream.length", 1);
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        CaptureOperationalSnapshotReader reader = mock(CaptureOperationalSnapshotReader.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        doReturn(streams).when(redis).opsForStream();

        CaptureProcessingProperties processing = new CaptureProcessingProperties();
        processing.setStreamKey("capture-stream");
        processing.setConsumerGroup("capture-group");
        AiProviderProperties providers = new AiProviderProperties();
        providers.setProvider("deepseek");
        AiProviderConcurrencyProperties concurrency = new AiProviderConcurrencyProperties();
        concurrency.setKeyPrefix("test:ai");
        concurrency.setProviderLimits(Map.of("deepseek", 2));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CaptureOperationalMetricsCollector collector = new CaptureOperationalMetricsCollector(
                reader,
                redis,
                processing,
                providers,
                concurrency,
                registry,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(collector, reader, redis, streams, registry);
    }

    private void assertGauge(SimpleMeterRegistry registry, String name, double expected) {
        assertThat(registry.get(name).gauge().value()).isEqualTo(expected);
    }

    private void assertGauge(
            SimpleMeterRegistry registry,
            String name,
            String tag,
            String value,
            double expected
    ) {
        assertThat(registry.get(name).tag(tag, value).gauge().value()).isEqualTo(expected);
    }

    private record Fixture(
            CaptureOperationalMetricsCollector collector,
            CaptureOperationalSnapshotReader reader,
            StringRedisTemplate redis,
            StreamOperations<String, String, String> streams,
            SimpleMeterRegistry registry
    ) {
    }
}

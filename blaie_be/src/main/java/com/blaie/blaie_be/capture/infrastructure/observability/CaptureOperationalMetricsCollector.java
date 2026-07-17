package com.blaie.blaie_be.capture.infrastructure.observability;

import com.blaie.blaie_be.capture.infrastructure.ai.AiProviderConcurrencyProperties;
import com.blaie.blaie_be.capture.infrastructure.ai.AiProviderProperties;
import com.blaie.blaie_be.capture.infrastructure.async.CaptureProcessingProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "blaie.capture.observability",
        name = "collector-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CaptureOperationalMetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(CaptureOperationalMetricsCollector.class);
    private static final String PROVIDER_ID_PATTERN = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}";
    static final DefaultRedisScript<Long> PROVIDER_USAGE = new DefaultRedisScript<>("""
            local redis_time = redis.call('TIME')
            local now_ms = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now_ms)
            local current = redis.call('ZCARD', KEYS[1])
            if current == 0 then
              redis.call('DEL', KEYS[1])
            end
            return current
            """, Long.class);

    private final CaptureOperationalSnapshotReader snapshotReader;
    private final StringRedisTemplate redisTemplate;
    private final CaptureProcessingProperties processingProperties;
    private final AiProviderConcurrencyProperties concurrencyProperties;
    private final Clock clock;
    private final AtomicLong queued = new AtomicLong();
    private final AtomicLong retryWait = new AtomicLong();
    private final AtomicLong processing = new AtomicLong();
    private final AtomicLong activeLeases = new AtomicLong();
    private final AtomicReference<Double> oldestQueuedAge = new AtomicReference<>(0.0);
    private final AtomicLong outboxBacklog = new AtomicLong();
    private final AtomicReference<Double> oldestOutboxAge = new AtomicReference<>(0.0);
    private final AtomicLong streamPending = new AtomicLong();
    private final AtomicLong streamLength = new AtomicLong();
    private final AtomicInteger databaseUp = new AtomicInteger();
    private final AtomicInteger redisUp = new AtomicInteger();
    private final AtomicLong databaseLastSuccess = new AtomicLong();
    private final AtomicLong redisLastSuccess = new AtomicLong();
    private final Map<String, AtomicLong> providerUsage = new ConcurrentHashMap<>();

    public CaptureOperationalMetricsCollector(
            CaptureOperationalSnapshotReader snapshotReader,
            StringRedisTemplate redisTemplate,
            CaptureProcessingProperties processingProperties,
            AiProviderProperties providerProperties,
            AiProviderConcurrencyProperties concurrencyProperties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.snapshotReader = snapshotReader;
        this.redisTemplate = redisTemplate;
        this.processingProperties = processingProperties;
        this.concurrencyProperties = concurrencyProperties;
        this.clock = clock;
        registerGauges(meterRegistry, configuredProviders(providerProperties, concurrencyProperties));
    }

    @Scheduled(
            initialDelayString = "${blaie.capture.observability.snapshot-interval:15s}",
            fixedDelayString = "${blaie.capture.observability.snapshot-interval:15s}",
            scheduler = CaptureObservabilityConfiguration.METRICS_SCHEDULER
    )
    public void refresh() {
        refreshDatabase();
        refreshRedis();
    }

    private void refreshDatabase() {
        try {
            Instant now = clock.instant();
            CaptureOperationalSnapshotReader.JobSnapshot jobs = snapshotReader.readJobs();
            CaptureOperationalSnapshotReader.OutboxSnapshot outbox = snapshotReader.readOutbox();
            queued.set(jobs.queued());
            retryWait.set(jobs.retryWait());
            processing.set(jobs.processing());
            activeLeases.set(jobs.activeLeases());
            oldestQueuedAge.set(ageSeconds(jobs.oldestQueuedAt(), now));
            outboxBacklog.set(outbox.backlog());
            oldestOutboxAge.set(ageSeconds(outbox.oldestPublicationAt(), now));
            databaseLastSuccess.set(now.getEpochSecond());
            databaseUp.set(1);
        } catch (RuntimeException exception) {
            databaseUp.set(0);
            log.warn("Capture database metrics snapshot failed: {}", exception.getMessage());
        }
    }

    private void refreshRedis() {
        try {
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            Long length = streams.size(processingProperties.streamKey());
            streamLength.set(length == null ? 0 : length);
            streamPending.set(readPending(streams));
            if (concurrencyProperties.enabled()) {
                for (Map.Entry<String, AtomicLong> entry : providerUsage.entrySet()) {
                    Long usage = redisTemplate.execute(
                            PROVIDER_USAGE,
                            List.of(concurrencyProperties.keyFor(entry.getKey()))
                    );
                    if (usage == null) {
                        throw new IllegalStateException("Redis provider usage returned no result");
                    }
                    entry.getValue().set(usage);
                }
            } else {
                providerUsage.values().forEach(value -> value.set(0));
            }
            Instant now = clock.instant();
            redisLastSuccess.set(now.getEpochSecond());
            redisUp.set(1);
        } catch (RuntimeException exception) {
            redisUp.set(0);
            log.warn("Capture Redis metrics snapshot failed: {}", exception.getMessage());
        }
    }

    private long readPending(StreamOperations<String, String, String> streams) {
        try {
            PendingMessagesSummary summary = streams.pending(
                    processingProperties.streamKey(),
                    processingProperties.consumerGroup()
            );
            return summary == null ? 0 : summary.getTotalPendingMessages();
        } catch (DataAccessException exception) {
            if (containsNoGroup(exception)) {
                return 0;
            }
            throw exception;
        }
    }

    private boolean containsNoGroup(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("NOGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void registerGauges(MeterRegistry registry, Set<String> providers) {
        Gauge.builder("capture.queue.depth", queued, AtomicLong::doubleValue)
                .description("Number of durable capture jobs by active state")
                .tag("state", "queued")
                .register(registry);
        Gauge.builder("capture.queue.depth", retryWait, AtomicLong::doubleValue)
                .description("Number of durable capture jobs by active state")
                .tag("state", "retry_wait")
                .register(registry);
        Gauge.builder("capture.queue.depth", processing, AtomicLong::doubleValue)
                .description("Number of durable capture jobs by active state")
                .tag("state", "processing")
                .register(registry);
        TimeGauge.builder(
                        "capture.oldest.queued.age",
                        oldestQueuedAge,
                        TimeUnit.SECONDS,
                        AtomicReference::get
                )
                .description("Age of the oldest queued capture job")
                .register(registry);
        Gauge.builder("capture.active.leases", activeLeases, AtomicLong::doubleValue)
                .description("Number of capture jobs with an active database lease")
                .register(registry);
        Gauge.builder("capture.outbox.backlog", outboxBacklog, AtomicLong::doubleValue)
                .description("Incomplete text-capture outbox publications")
                .register(registry);
        TimeGauge.builder(
                        "capture.outbox.oldest.age",
                        oldestOutboxAge,
                        TimeUnit.SECONDS,
                        AtomicReference::get
                )
                .description("Age of the oldest incomplete text-capture outbox publication")
                .register(registry);
        Gauge.builder("capture.redis.stream.pending", streamPending, AtomicLong::doubleValue)
                .description("Delivered but unacknowledged capture Redis Stream entries")
                .register(registry);
        Gauge.builder("capture.redis.stream.length", streamLength, AtomicLong::doubleValue)
                .description("Capture Redis Stream length")
                .register(registry);
        sourceGauges(registry, "db", databaseUp, databaseLastSuccess);
        sourceGauges(registry, "redis", redisUp, redisLastSuccess);

        for (String provider : providers) {
            AtomicLong usage = new AtomicLong();
            providerUsage.put(provider, usage);
            Gauge.builder("capture.provider.concurrency.usage", usage, AtomicLong::doubleValue)
                    .description("Distributed AI provider concurrency slots currently in use")
                    .tag("provider", provider)
                    .register(registry);
            Gauge.builder(
                            "capture.provider.concurrency.limit",
                            provider,
                            value -> concurrencyProperties.limitFor(value)
                    )
                    .description("Configured distributed AI provider concurrency limit")
                    .tag("provider", provider)
                    .register(registry);
        }
    }

    private void sourceGauges(
            MeterRegistry registry,
            String source,
            AtomicInteger up,
            AtomicLong lastSuccess
    ) {
        Gauge.builder("capture.observability.source.up", up, AtomicInteger::doubleValue)
                .description("Whether the most recent capture metrics source snapshot succeeded")
                .tag("source", source)
                .register(registry);
        Gauge.builder(
                        "capture.observability.source.last.success",
                        lastSuccess,
                        AtomicLong::doubleValue
                )
                .description("Unix timestamp of the most recent successful capture metrics source snapshot")
                .baseUnit("seconds")
                .tag("source", source)
                .register(registry);
    }

    private Set<String> configuredProviders(
            AiProviderProperties providerProperties,
            AiProviderConcurrencyProperties limits
    ) {
        Set<String> providers = new LinkedHashSet<>();
        addProvider(providers, providerProperties.provider());
        providerProperties.fallbackProviders().forEach(provider -> addProvider(providers, provider));
        limits.providerLimits().keySet().forEach(provider -> addProvider(providers, provider));
        return Set.copyOf(providers);
    }

    private void addProvider(Set<String> providers, String provider) {
        if (provider != null && provider.matches(PROVIDER_ID_PATTERN)) {
            providers.add(provider.toLowerCase(Locale.ROOT));
        }
    }

    private double ageSeconds(Instant oldest, Instant now) {
        if (oldest == null || oldest.isAfter(now)) {
            return 0;
        }
        return Duration.between(oldest, now).toMillis() / 1_000.0;
    }
}

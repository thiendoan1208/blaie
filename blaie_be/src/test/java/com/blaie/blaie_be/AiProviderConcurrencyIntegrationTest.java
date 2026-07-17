package com.blaie.blaie_be;

import com.blaie.blaie_be.capture.infrastructure.ai.AiProviderConcurrencyProperties;
import com.blaie.blaie_be.capture.infrastructure.ai.ProviderConcurrencyLimiter;
import com.blaie.blaie_be.capture.infrastructure.ai.RedisProviderConcurrencyLimiter;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "blaie.capture.processing.accept-async-enabled=false",
        "blaie.capture.processing.publisher-enabled=false",
        "blaie.capture.processing.worker-enabled=false",
        "blaie.capture.processing.recovery-enabled=false",
        "blaie.auth.access-token-secret=concurrency-test-access-secret-at-least-32-bytes",
        "blaie.email.provider=log",
        "blaie.email.from=Blaie <no-reply@test.local>",
        "blaie.email.web-base-url=http://localhost:3000",
        "blaie.email.api-base-url=http://localhost:8080/api/v1",
        "blaie.email.verification-ttl=24h",
        "blaie.google.oauth.client-id=test-google-client-id",
        "blaie.google.oauth.client-secret=test-google-client-secret",
        "blaie.google.oauth.redirect-uri=http://localhost:8080/api/v1/auth/google/callback",
        "blaie.google.oauth.web-base-url=http://localhost:3000"
})
class AiProviderConcurrencyIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CaptureTelemetryPort telemetry;

    @Test
    void renewedPermitStaysOwnedBeyondItsOriginalLeaseAndCloseUnblocksTheWaiter() throws Exception {
        AiProviderConcurrencyProperties properties = properties(Duration.ofMillis(300));
        ThreadPoolTaskScheduler renewalScheduler = scheduler("test-ai-renewal-");
        RedisProviderConcurrencyLimiter firstLimiter =
                new RedisProviderConcurrencyLimiter(redisTemplate, properties, renewalScheduler, telemetry);
        RedisProviderConcurrencyLimiter secondLimiter =
                new RedisProviderConcurrencyLimiter(redisTemplate, properties, renewalScheduler, telemetry);
        ProviderConcurrencyLimiter.Permit firstPermit = firstLimiter.acquire("deepseek");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<ProviderConcurrencyLimiter.Permit> waiting = CompletableFuture.supplyAsync(
                    () -> secondLimiter.acquire("deepseek"),
                    executor
            );
            assertThatThrownBy(() -> waiting.get(800, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            firstPermit.close();
            ProviderConcurrencyLimiter.Permit secondPermit = waiting.get(1, TimeUnit.SECONDS);
            secondPermit.close();
        } finally {
            firstPermit.close();
            renewalScheduler.shutdown();
        }
    }

    @Test
    void stoppedRenewalAllowsTtlReclaimAndStaleOwnerCannotReleaseTheReplacement() throws Exception {
        AiProviderConcurrencyProperties properties = properties(Duration.ofMillis(300));
        ThreadPoolTaskScheduler crashedScheduler = scheduler("test-ai-crashed-renewal-");
        ThreadPoolTaskScheduler liveScheduler = scheduler("test-ai-live-renewal-");
        RedisProviderConcurrencyLimiter crashedLimiter =
                new RedisProviderConcurrencyLimiter(redisTemplate, properties, crashedScheduler, telemetry);
        RedisProviderConcurrencyLimiter liveLimiter =
                new RedisProviderConcurrencyLimiter(redisTemplate, properties, liveScheduler, telemetry);
        ProviderConcurrencyLimiter.Permit expiredPermit = crashedLimiter.acquire("deepseek");
        crashedScheduler.shutdown();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ProviderConcurrencyLimiter.Permit replacementPermit = CompletableFuture.supplyAsync(
                    () -> liveLimiter.acquire("deepseek"),
                    executor
            ).get(2, TimeUnit.SECONDS);

            expiredPermit.close();
            CompletableFuture<ProviderConcurrencyLimiter.Permit> nextWaiting = CompletableFuture.supplyAsync(
                    () -> liveLimiter.acquire("deepseek"),
                    executor
            );
            assertThatThrownBy(() -> nextWaiting.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            replacementPermit.close();
            nextWaiting.get(1, TimeUnit.SECONDS).close();
        } finally {
            expiredPermit.close();
            crashedScheduler.shutdown();
            liveScheduler.shutdown();
        }
    }

    private AiProviderConcurrencyProperties properties(Duration leaseDuration) {
        AiProviderConcurrencyProperties properties = new AiProviderConcurrencyProperties();
        properties.setKeyPrefix("test:ai:concurrency:" + UUID.randomUUID());
        properties.setLeaseDuration(leaseDuration);
        properties.setRenewalInterval(leaseDuration.dividedBy(4));
        properties.setPollInterval(Duration.ofMillis(10));
        return properties;
    }

    private ThreadPoolTaskScheduler scheduler(String threadPrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadPrefix);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }
}

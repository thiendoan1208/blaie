package com.blaie.blaie_be.capture.infrastructure.ai;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderConcurrencyConfigurationTest {

    @Test
    void renewalUsesOneSharedBoundedSpringSchedulerWithNamedThreads() throws Exception {
        AiProviderConcurrencyProperties properties = new AiProviderConcurrencyProperties();
        properties.setRenewalPoolSize(2);
        ThreadPoolTaskScheduler scheduler = new AiProviderConcurrencyConfiguration()
                .aiProviderConcurrencyRenewalScheduler(properties);
        scheduler.initialize();
        CompletableFuture<String> threadName = new CompletableFuture<>();

        try {
            scheduler.schedule(
                    () -> threadName.complete(Thread.currentThread().getName()),
                    Instant.now()
            );

            assertThat(threadName.get(1, TimeUnit.SECONDS))
                    .startsWith("ai-concurrency-renewal-");
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize())
                    .isEqualTo(2);
        } finally {
            scheduler.shutdown();
        }
    }
}

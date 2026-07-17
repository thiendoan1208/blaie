package com.blaie.blaie_be.capture.infrastructure.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AiProviderConcurrencyConfiguration {
    public static final String RENEWAL_SCHEDULER = "aiProviderConcurrencyRenewalScheduler";

    @Bean(name = RENEWAL_SCHEDULER)
    ThreadPoolTaskScheduler aiProviderConcurrencyRenewalScheduler(
            AiProviderConcurrencyProperties properties
    ) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.renewalPoolSize());
        scheduler.setThreadNamePrefix("ai-concurrency-renewal-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAcceptTasksAfterContextClose(false);
        return scheduler;
    }
}

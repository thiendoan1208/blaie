package com.blaie.blaie_be.retention.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class RetentionSchedulingConfiguration {
    public static final String RETENTION_SCHEDULER = "retentionTaskScheduler";

    @Bean(name = RETENTION_SCHEDULER)
    ThreadPoolTaskScheduler retentionTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("retention-cleanup-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAcceptTasksAfterContextClose(false);
        return scheduler;
    }
}

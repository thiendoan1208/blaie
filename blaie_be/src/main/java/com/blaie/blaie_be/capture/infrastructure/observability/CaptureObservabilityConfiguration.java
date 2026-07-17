package com.blaie.blaie_be.capture.infrastructure.observability;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class CaptureObservabilityConfiguration {
    public static final String METRICS_SCHEDULER = "captureMetricsScheduler";

    @Bean(name = METRICS_SCHEDULER)
    TaskScheduler captureMetricsScheduler(CaptureObservabilityProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.schedulerPoolSize());
        scheduler.setThreadNamePrefix("capture-metrics-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAcceptTasksAfterContextClose(false);
        return scheduler;
    }

    @Bean
    MeterFilter captureProviderDurationCardinalityGuard(CaptureObservabilityProperties properties) {
        return providerCardinalityGuard("capture.provider.duration", properties);
    }

    @Bean
    MeterFilter captureProviderErrorCardinalityGuard(CaptureObservabilityProperties properties) {
        return providerCardinalityGuard("capture.provider.errors", properties);
    }

    @Bean
    MeterFilter captureProviderConcurrencyCardinalityGuard(CaptureObservabilityProperties properties) {
        return providerCardinalityGuard("capture.provider.concurrency", properties);
    }

    private MeterFilter providerCardinalityGuard(
            String meterNamePrefix,
            CaptureObservabilityProperties properties
    ) {
        return MeterFilter.maximumAllowableTags(
                meterNamePrefix,
                "provider",
                properties.maxProviderTagValues(),
                MeterFilter.deny()
        );
    }
}

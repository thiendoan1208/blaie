package com.blaie.blaie_be.capture.infrastructure.async;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class CaptureAsyncConfiguration {
    public static final String JOB_EXECUTOR = "captureJobExecutor";
    public static final String TASK_SCHEDULER = "taskScheduler";
    public static final String HEARTBEAT_SCHEDULER = "captureJobHeartbeatScheduler";
    public static final String EVENT_EXECUTOR = "captureEventExecutor";

    @Bean(name = TASK_SCHEDULER)
    ThreadPoolTaskScheduler captureTaskScheduler(CaptureProcessingProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.schedulerPoolSize());
        scheduler.setThreadNamePrefix("capture-scheduler-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAcceptTasksAfterContextClose(false);
        return scheduler;
    }

    @Bean(name = HEARTBEAT_SCHEDULER)
    TaskScheduler captureJobHeartbeatScheduler(CaptureProcessingProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.heartbeatPoolSize());
        scheduler.setThreadNamePrefix("capture-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAcceptTasksAfterContextClose(false);
        return scheduler;
    }

    @Bean(name = JOB_EXECUTOR)
    ThreadPoolTaskExecutor captureJobExecutor(
            CaptureProcessingProperties properties,
            @Qualifier(HEARTBEAT_SCHEDULER) TaskScheduler heartbeatScheduler
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerCorePoolSize());
        executor.setMaxPoolSize(properties.workerMaxPoolSize());
        executor.setQueueCapacity(properties.workerQueueCapacity());
        executor.setKeepAliveSeconds(Math.toIntExact(properties.workerKeepAlive().toSeconds()));
        executor.setThreadNamePrefix("capture-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setAcceptTasksAfterContextClose(false);
        executor.setStrictEarlyShutdown(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.toIntExact(properties.workerShutdownAwait().toSeconds()));
        return executor;
    }

    @Bean(name = {EVENT_EXECUTOR, "taskExecutor"})
    ThreadPoolTaskExecutor captureEventExecutor(CaptureProcessingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.eventCorePoolSize());
        executor.setMaxPoolSize(properties.eventMaxPoolSize());
        executor.setQueueCapacity(properties.eventQueueCapacity());
        executor.setKeepAliveSeconds(Math.toIntExact(properties.workerKeepAlive().toSeconds()));
        executor.setThreadNamePrefix("capture-events-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setAcceptTasksAfterContextClose(false);
        executor.setStrictEarlyShutdown(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.toIntExact(properties.workerShutdownAwait().toSeconds()));
        return executor;
    }
}

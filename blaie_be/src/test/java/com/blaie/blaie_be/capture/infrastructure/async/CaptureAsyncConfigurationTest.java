package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.infrastructure.ai.AiProviderConcurrencyConfiguration;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CaptureAsyncConfigurationTest {

    @Test
    void scheduledTriggersUseTheirOwnConventionalSchedulerBean() throws Exception {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setSchedulerPoolSize(1);
        CaptureAsyncConfiguration configuration = new CaptureAsyncConfiguration();
        Bean bean = CaptureAsyncConfiguration.class
                .getDeclaredMethod("captureTaskScheduler", CaptureProcessingProperties.class)
                .getAnnotation(Bean.class);
        ThreadPoolTaskScheduler scheduler = configuration.captureTaskScheduler(properties);
        scheduler.initialize();
        CompletableFuture<String> threadName = new CompletableFuture<>();

        try {
            scheduler.execute(() -> threadName.complete(Thread.currentThread().getName()));

            assertThat(bean.name()).containsExactly(CaptureAsyncConfiguration.TASK_SCHEDULER);
            assertThat(threadName.get(1, TimeUnit.SECONDS)).startsWith("capture-scheduler-");
            assertThat(CaptureAsyncConfiguration.TASK_SCHEDULER)
                    .isNotEqualTo(CaptureAsyncConfiguration.HEARTBEAT_SCHEDULER)
                    .isNotEqualTo(AiProviderConcurrencyConfiguration.RENEWAL_SCHEDULER);
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void jobExecutorIsBoundedAndUsesDedicatedWorkerThreads() throws Exception {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setWorkerCorePoolSize(1);
        properties.setWorkerMaxPoolSize(1);
        properties.setWorkerQueueCapacity(0);
        ThreadPoolTaskExecutor executor = executor(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> threadName = new CompletableFuture<>();

        try {
            executor.execute(() -> {
                threadName.complete(Thread.currentThread().getName());
                started.countDown();
                await(release);
            });
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> executor.execute(() -> { }))
                    .isInstanceOf(TaskRejectedException.class);
            assertThat(threadName.get(1, TimeUnit.SECONDS)).startsWith("capture-worker-");
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void shutdownStopsAdmissionAndWaitsForRunningJobWithinConfiguredBound() throws Exception {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setWorkerShutdownAwait(Duration.ofSeconds(2));
        ThreadPoolTaskExecutor executor = executor(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            await(release);
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> shutdown = CompletableFuture.runAsync(executor::shutdown);
        Thread.sleep(100);
        assertThat(shutdown).isNotDone();
        assertThatThrownBy(() -> executor.execute(() -> { }))
                .isInstanceOf(TaskRejectedException.class);

        release.countDown();
        shutdown.get(1, TimeUnit.SECONDS);
        assertThat(shutdown).isCompleted();
    }

    @Test
    void shutdownInterruptsRunningJobAfterConfiguredBound() throws Exception {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setWorkerShutdownAwait(Duration.ofSeconds(1));
        ThreadPoolTaskExecutor executor = executor(properties);
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        executor.execute(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        long startedAt = System.nanoTime();
        try {
            executor.shutdown();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(elapsedMillis).isBetween(800L, 2_500L);
            assertThat(interrupted).isTrue();
            assertThat(executor.getThreadPoolExecutor().isTerminated()).isTrue();
        } finally {
            executor.getThreadPoolExecutor().shutdownNow();
        }
    }

    @Test
    void eventExecutorIsBoundedAndHasTheDefaultAsyncExecutorName() throws Exception {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        CaptureAsyncConfiguration configuration = new CaptureAsyncConfiguration();
        Bean bean = CaptureAsyncConfiguration.class
                .getDeclaredMethod("captureEventExecutor", CaptureProcessingProperties.class)
                .getAnnotation(Bean.class);
        ThreadPoolTaskExecutor executor = configuration.captureEventExecutor(properties);
        executor.initialize();
        CompletableFuture<String> threadName = new CompletableFuture<>();

        try {
            executor.execute(() -> threadName.complete(Thread.currentThread().getName()));

            assertThat(threadName.get(1, TimeUnit.SECONDS)).startsWith("capture-events-");
            assertThat(bean.name()).containsExactly(CaptureAsyncConfiguration.EVENT_EXECUTOR, "taskExecutor");
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
        } finally {
            executor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor executor(CaptureProcessingProperties properties) {
        ThreadPoolTaskExecutor executor = new CaptureAsyncConfiguration().captureJobExecutor(
                properties,
                mock(TaskScheduler.class)
        );
        executor.initialize();
        return executor;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

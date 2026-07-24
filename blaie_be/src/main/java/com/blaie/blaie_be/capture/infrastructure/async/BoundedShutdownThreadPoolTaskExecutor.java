package com.blaie.blaie_be.capture.infrastructure.async;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Preserves Spring's graceful wait, then interrupts work that is still blocking
 * so a terminated application is not kept alive indefinitely by worker threads.
 */
final class BoundedShutdownThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
    private static final long INTERRUPT_SETTLE_MILLIS = 100;

    @Override
    public void shutdown() {
        super.shutdown();

        ThreadPoolExecutor executor;
        try {
            executor = getThreadPoolExecutor();
        } catch (IllegalStateException exception) {
            return;
        }
        if (executor.isTerminated()) {
            return;
        }

        executor.shutdownNow().forEach(this::cancelRemainingTask);
        try {
            executor.awaitTermination(INTERRUPT_SETTLE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

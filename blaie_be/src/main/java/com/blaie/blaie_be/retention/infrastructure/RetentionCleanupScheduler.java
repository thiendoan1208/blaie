package com.blaie.blaie_be.retention.infrastructure;

import com.blaie.blaie_be.retention.application.port.RetentionCleanupStorePort;
import java.time.Clock;
import java.time.Instant;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetentionCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupScheduler.class);

    private final RetentionCleanupStorePort store;
    private final RetentionProperties properties;
    private final Clock clock;

    public RetentionCleanupScheduler(
            RetentionCleanupStorePort store,
            RetentionProperties properties,
            Clock clock
    ) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${blaie.retention.cleanup-interval:1h}",
            scheduler = RetentionSchedulingConfiguration.RETENTION_SCHEDULER
    )
    public void cleanup() {
        if (!properties.enabled()) return;
        Instant now = clock.instant();
        cleanupCategory("expired_idempotency", () -> store.deleteExpiredIdempotencyKeys(now, properties.batchSize()));
        cleanupCategory("completed_outbox", () -> store.deleteCompletedOutboxEvents(
                now.minus(properties.completedOutbox()), properties.batchSize()));
        cleanupCategory("completed_processing_jobs", () -> store.deleteCompletedProcessingJobs(
                now.minus(properties.completedProcessingJobs()), properties.batchSize()));
        cleanupCategory("audit_events", () -> store.deleteExpiredAuditEvents(
                now.minus(properties.auditEvents()), properties.batchSize()));
    }

    private void cleanupCategory(String category, IntSupplier deleteBatch) {
        int total = 0;
        try {
            for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
                int deleted = deleteBatch.getAsInt();
                total += deleted;
                if (deleted < properties.batchSize()) break;
            }
            if (total > 0) {
                log.info("Retention cleanup completed: category={}, deleted={}", category, total);
            }
        } catch (RuntimeException exception) {
            log.error("Retention cleanup failed: category={}", category, exception);
        }
    }
}

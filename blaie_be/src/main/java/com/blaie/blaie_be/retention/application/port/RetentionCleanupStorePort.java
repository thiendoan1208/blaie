package com.blaie.blaie_be.retention.application.port;

import java.time.Instant;

public interface RetentionCleanupStorePort {
    int deleteExpiredIdempotencyKeys(Instant now, int batchSize);

    int deleteCompletedOutboxEvents(Instant cutoff, int batchSize);

    int deleteCompletedProcessingJobs(Instant cutoff, int batchSize);

    int deleteExpiredAuditEvents(Instant cutoff, int batchSize);
}

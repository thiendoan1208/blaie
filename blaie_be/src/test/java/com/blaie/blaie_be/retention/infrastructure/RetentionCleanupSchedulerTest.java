package com.blaie.blaie_be.retention.infrastructure;

import com.blaie.blaie_be.retention.application.port.RetentionCleanupStorePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionCleanupSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void drainsBoundedBatchesUsingIndependentRetentionCutoffs() {
        RetentionCleanupStorePort store = mock(RetentionCleanupStorePort.class);
        RetentionProperties properties = new RetentionProperties();
        properties.setBatchSize(2);
        properties.setMaxBatchesPerRun(2);
        properties.setCompletedOutbox(Duration.ofDays(7));
        properties.setCompletedProcessingJobs(Duration.ofDays(90));
        properties.setAuditEvents(Duration.ofDays(365));
        when(store.deleteExpiredIdempotencyKeys(NOW, 2)).thenReturn(2, 1);
        when(store.deleteCompletedOutboxEvents(NOW.minus(Duration.ofDays(7)), 2)).thenReturn(0);
        when(store.deleteCompletedProcessingJobs(NOW.minus(Duration.ofDays(90)), 2)).thenReturn(0);
        when(store.deleteExpiredAuditEvents(NOW.minus(Duration.ofDays(365)), 2)).thenReturn(0);

        new RetentionCleanupScheduler(
                store,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        ).cleanup();

        verify(store, times(2)).deleteExpiredIdempotencyKeys(NOW, 2);
        verify(store).deleteCompletedOutboxEvents(NOW.minus(Duration.ofDays(7)), 2);
        verify(store).deleteCompletedProcessingJobs(NOW.minus(Duration.ofDays(90)), 2);
        verify(store).deleteExpiredAuditEvents(NOW.minus(Duration.ofDays(365)), 2);
    }
}

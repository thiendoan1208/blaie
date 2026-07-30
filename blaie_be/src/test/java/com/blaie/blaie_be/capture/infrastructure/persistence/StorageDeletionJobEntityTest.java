package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageDeletionJobEntityTest {
    @Test
    void durableDeletionJobUsesLeaseAndRetryStateTransitions() {
        Instant now = Instant.parse("2026-07-28T12:00:00Z");
        StorageDeletionJobEntity job = StorageDeletionJobEntity.pending(
                "captures/asset.png",
                8,
                now
        );

        assertThat(job.claim("delete-worker", now, now.plusSeconds(30))).isTrue();
        assertThat(job.status()).isEqualTo("processing");
        assertThat(job.attemptCount()).isEqualTo(1);

        assertThat(job.retry(
                "delete-worker",
                1,
                "storage_delete_failed",
                now.plus(Duration.ofMinutes(1)),
                now
        )).isTrue();
        assertThat(job.status()).isEqualTo("retry_wait");
        assertThat(job.availableAt()).isEqualTo(now.plusSeconds(60));

        assertThat(job.claim("delete-worker", now.plusSeconds(60), now.plusSeconds(90))).isTrue();
        assertThat(job.complete("delete-worker", 2, now.plusSeconds(61))).isTrue();
        assertThat(job.status()).isEqualTo("completed");
        assertThat(job.completedAt()).isEqualTo(now.plusSeconds(61));
    }

    @Test
    void staleDeletionWorkerCannotCompleteOrRetryANewerLease() {
        Instant now = Instant.parse("2026-07-28T12:00:00Z");
        StorageDeletionJobEntity job = StorageDeletionJobEntity.pending(
                "captures/asset.png",
                8,
                now
        );
        assertThat(job.claim("worker-a", now, now.plusSeconds(30))).isTrue();
        job.recover(now.plusSeconds(31));
        assertThat(job.claim("worker-b", now.plusSeconds(31), now.plusSeconds(61))).isTrue();

        assertThat(job.complete("worker-a", 1, now.plusSeconds(32))).isFalse();
        assertThat(job.retry(
                "worker-a",
                1,
                "late_failure",
                now.plusSeconds(90),
                now.plusSeconds(32)
        )).isFalse();
        assertThat(job.status()).isEqualTo("processing");

        assertThat(job.complete("worker-b", 2, now.plusSeconds(33))).isTrue();
        assertThat(job.status()).isEqualTo("completed");
    }
}

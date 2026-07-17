package com.blaie.blaie_be.capture.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingJobEntityTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void onlyCurrentLeaseOwnerCanExtendProcessingJobLease() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "Buy milk");
        ProcessingJobEntity job = ProcessingJobEntity.queued(capture, 4, NOW);
        Instant initialExpiry = NOW.plusSeconds(30);

        assertThat(job.claim("worker-1", NOW, initialExpiry)).isTrue();
        assertThat(job.extendLease("worker-2", NOW.plusSeconds(60))).isFalse();
        assertThat(job.leaseExpiresAt()).isEqualTo(initialExpiry);

        Instant extendedExpiry = NOW.plusSeconds(90);
        assertThat(job.extendLease("worker-1", extendedExpiry)).isTrue();
        assertThat(job.leaseExpiresAt()).isEqualTo(extendedExpiry);
    }

    @Test
    void terminalJobCannotExtendLease() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "Buy milk");
        ProcessingJobEntity job = ProcessingJobEntity.queued(capture, 4, NOW);
        job.claim("worker-1", NOW, NOW.plusSeconds(30));
        job.complete(NOW.plusSeconds(1));

        assertThat(job.extendLease("worker-1", NOW.plusSeconds(60))).isFalse();
        assertThat(job.leaseExpiresAt()).isNull();
    }
}

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
        ProcessingJobEntity job = queuedJob(capture);
        Instant initialExpiry = NOW.plusSeconds(30);

        assertThat(job.claim(1, "worker-1", NOW, initialExpiry)).isTrue();
        assertThat(job.extendLease("worker-2", 1, 0, NOW.plusSeconds(60))).isFalse();
        assertThat(job.leaseExpiresAt()).isEqualTo(initialExpiry);

        Instant extendedExpiry = NOW.plusSeconds(90);
        assertThat(job.extendLease("worker-1", 1, 0, extendedExpiry)).isTrue();
        assertThat(job.leaseExpiresAt()).isEqualTo(extendedExpiry);
    }

    @Test
    void terminalJobCannotExtendLease() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "Buy milk");
        ProcessingJobEntity job = queuedJob(capture);
        job.claim(1, "worker-1", NOW, NOW.plusSeconds(30));
        job.complete(NOW.plusSeconds(1));

        assertThat(job.extendLease("worker-1", 1, 0, NOW.plusSeconds(60))).isFalse();
        assertThat(job.leaseExpiresAt()).isNull();
    }

    @Test
    void queuedJobTracksEachDurableDispatchAndRejectsOldMessages() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "Buy milk");
        ProcessingJobEntity job = queuedJob(capture);

        assertThat(job.dispatchGeneration()).isEqualTo(1);
        assertThat(job.lastDispatchedAt()).isEqualTo(NOW);
        assertThat(job.nextDispatchAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(job.claim(0, "worker-old-message", NOW, NOW.plusSeconds(30))).isFalse();

        Instant redispatchedAt = NOW.plusSeconds(30);
        job.dispatch(redispatchedAt, redispatchedAt.plusSeconds(120));

        assertThat(job.dispatchGeneration()).isEqualTo(2);
        assertThat(job.lastDispatchedAt()).isEqualTo(redispatchedAt);
        assertThat(job.nextDispatchAt()).isEqualTo(redispatchedAt.plusSeconds(120));
        assertThat(job.claim(1, "worker-old-message", redispatchedAt, redispatchedAt.plusSeconds(30))).isFalse();
        assertThat(job.claim(2, "worker-current", redispatchedAt, redispatchedAt.plusSeconds(30))).isTrue();
        assertThat(job.nextDispatchAt()).isNull();
    }

    @Test
    void previousAttemptCannotOwnLeaseAfterJobIsClaimedAgain() {
        CaptureEntity capture = CaptureEntity.processing(UUID.randomUUID(), "Buy milk");
        ProcessingJobEntity job = queuedJob(capture);

        assertThat(job.claim(1, "worker-1", NOW, NOW.plusSeconds(30))).isTrue();
        job.scheduleRetry("provider_unavailable", NOW.plusSeconds(31));
        job.dispatch(NOW.plusSeconds(31), NOW.plusSeconds(61));
        assertThat(job.claim(2, "worker-2", NOW.plusSeconds(31), NOW.plusSeconds(61))).isTrue();

        assertThat(job.ownsLease("worker-1", 1, 0)).isFalse();
        assertThat(job.extendLease("worker-1", 1, 0, NOW.plusSeconds(90))).isFalse();
        assertThat(job.ownsLease("worker-2", 2, 0)).isTrue();
    }

    private ProcessingJobEntity queuedJob(CaptureEntity capture) {
        return ProcessingJobEntity.queued(capture, 4, NOW, NOW.plusSeconds(30));
    }
}

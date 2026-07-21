package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledJobLeaseHeartbeatAdapterTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void heartbeatExtendsLeaseAndStopsAfterProcessing() {
        ProcessingJobStorePort jobStore = mock(ProcessingJobStorePort.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> scheduledFuture = mock();
        ArgumentCaptor<Runnable> heartbeatTask = ArgumentCaptor.forClass(Runnable.class);
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        UUID jobId = UUID.randomUUID();
        doReturn(scheduledFuture).when(scheduler).scheduleAtFixedRate(
                heartbeatTask.capture(),
                eq(NOW.plusSeconds(10)),
                eq(Duration.ofSeconds(10))
        );
        when(jobStore.extendLease(any(), any(), any(Integer.class), any(Integer.class), any())).thenReturn(true);
        ScheduledJobLeaseHeartbeatAdapter adapter = new ScheduledJobLeaseHeartbeatAdapter(
                jobStore,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                scheduler
        );

        var heartbeat = adapter.start(jobId, "worker-1", 2, 3);
        heartbeatTask.getValue().run();
        heartbeat.stop();

        verify(jobStore).extendLease(jobId, "worker-1", 2, 3, NOW.plusSeconds(30));
        verify(scheduledFuture).cancel(false);
        assertThat(properties.heartbeatInterval()).isLessThan(properties.leaseDuration());
    }
}

package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.port.CaptureProcessingSettingsPort;
import com.blaie.blaie_be.capture.application.port.JobLeaseHeartbeatPort;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobLeaseHeartbeatAdapter implements JobLeaseHeartbeatPort {
    private static final Logger log = LoggerFactory.getLogger(ScheduledJobLeaseHeartbeatAdapter.class);

    private final ProcessingJobStorePort jobStore;
    private final CaptureProcessingSettingsPort settings;
    private final Clock clock;
    private final TaskScheduler scheduler;

    public ScheduledJobLeaseHeartbeatAdapter(
            ProcessingJobStorePort jobStore,
            CaptureProcessingSettingsPort settings,
            Clock clock,
            @Qualifier(CaptureAsyncConfiguration.HEARTBEAT_SCHEDULER) TaskScheduler scheduler
    ) {
        this.jobStore = jobStore;
        this.settings = settings;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    @Override
    public ActiveHeartbeat start(UUID jobId, String workerId) {
        Instant firstHeartbeat = clock.instant().plus(settings.heartbeatInterval());
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> extendLease(jobId, workerId),
                firstHeartbeat,
                settings.heartbeatInterval()
        );
        return () -> future.cancel(false);
    }

    private void extendLease(UUID jobId, String workerId) {
        Instant now = clock.instant();
        try {
            boolean extended = jobStore.extendLease(
                    jobId,
                    workerId,
                    now.plus(settings.leaseDuration())
            );
            if (!extended) {
                log.debug("Capture job lease is no longer owned: jobId={}, workerId={}", jobId, workerId);
            }
        } catch (RuntimeException exception) {
            log.warn("Capture job lease heartbeat failed: jobId={}, workerId={}", jobId, workerId);
        }
    }
}

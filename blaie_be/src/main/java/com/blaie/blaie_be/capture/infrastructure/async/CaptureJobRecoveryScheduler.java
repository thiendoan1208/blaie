package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.DeadSource;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort.RetrySource;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult.RecoveryOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "blaie.capture.processing",
        name = "recovery-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CaptureJobRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(CaptureJobRecoveryScheduler.class);
    private final ProcessingJobStorePort jobStore;
    private final IncompleteEventPublications eventPublications;
    private final CaptureProcessingProperties properties;
    private final Clock clock;
    private final CaptureTelemetryPort telemetry;

    public CaptureJobRecoveryScheduler(
            ProcessingJobStorePort jobStore,
            IncompleteEventPublications eventPublications,
            CaptureProcessingProperties properties,
            Clock clock,
            CaptureTelemetryPort telemetry
    ) {
        this.jobStore = jobStore;
        this.eventPublications = eventPublications;
        this.properties = properties;
        this.clock = clock;
        this.telemetry = telemetry;
    }

    @Scheduled(fixedDelayString = "${blaie.capture.processing.recovery-interval:2s}")
    public void recoverJobs() {
        Instant now = clock.instant();
        List<RecoveredJobResult> staleJobs = jobStore.recoverStale(now);
        int staleCount = staleJobs.size();
        int retryDispatchCount = jobStore.dispatchReadyRetries(now, properties.batchSize());
        int queuedRedispatchCount = jobStore.redispatchStaleQueued(now, properties.batchSize());
        telemetry.incrementStaleRecovered(staleCount);
        for (RecoveredJobResult recovered : staleJobs) {
            if (recovered.outcome() == RecoveryOutcome.RETRY_SCHEDULED) {
                telemetry.incrementRetry(RetrySource.STALE_RECOVERY);
            } else {
                telemetry.incrementDead(DeadSource.STALE_RECOVERY, recovered.failureClass());
            }
        }
        telemetry.incrementQueuedRedispatched(queuedRedispatchCount);
        if (staleCount > 0 || retryDispatchCount > 0 || queuedRedispatchCount > 0) {
            log.info(
                    "Capture job recovery cycle: staleRecovered={}, retriesDispatched={}, queuedRedispatched={}",
                    staleCount,
                    retryDispatchCount,
                    queuedRedispatchCount
            );
        }
    }

    @Scheduled(fixedDelayString = "${blaie.capture.processing.outbox-recovery-interval:10s}")
    public void recoverOutbox() {
        Instant cutoff = clock.instant().minus(properties.outboxRecoveryAge());
        eventPublications.resubmitIncompletePublications(publication ->
                publication.getEvent() instanceof TextCaptureQueuedEvent
                        && publication.getPublicationDate().isBefore(cutoff)
        );
    }
}

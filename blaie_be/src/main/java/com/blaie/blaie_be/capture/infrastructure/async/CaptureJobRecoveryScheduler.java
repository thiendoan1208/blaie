package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "blaie.capture.processing",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CaptureJobRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(CaptureJobRecoveryScheduler.class);
    private final ProcessingJobStorePort jobStore;
    private final IncompleteEventPublications eventPublications;
    private final CaptureProcessingProperties properties;
    private final Clock clock;

    public CaptureJobRecoveryScheduler(
            ProcessingJobStorePort jobStore,
            IncompleteEventPublications eventPublications,
            CaptureProcessingProperties properties,
            Clock clock
    ) {
        this.jobStore = jobStore;
        this.eventPublications = eventPublications;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${blaie.capture.processing.recovery-interval:2s}")
    public void recoverJobs() {
        Instant now = clock.instant();
        int staleCount = jobStore.recoverStale(now, properties.retryDelay(1)).size();
        int dispatchedCount = jobStore.dispatchReadyRetries(now, properties.batchSize());
        if (staleCount > 0 || dispatchedCount > 0) {
            log.info(
                    "Capture job recovery cycle: staleRecovered={}, retriesDispatched={}",
                    staleCount,
                    dispatchedCount
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

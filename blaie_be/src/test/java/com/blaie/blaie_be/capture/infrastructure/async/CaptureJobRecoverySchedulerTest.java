package com.blaie.blaie_be.capture.infrastructure.async;

import com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent;
import com.blaie.blaie_be.capture.application.port.ProcessingJobStorePort;
import com.blaie.blaie_be.capture.application.port.CaptureTelemetryPort;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult;
import com.blaie.blaie_be.capture.application.result.RecoveredJobResult.RecoveryOutcome;
import com.blaie.blaie_be.capture.domain.TextClassificationFailureClass;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptureJobRecoverySchedulerTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void recoversStaleLeasesReadyRetriesAndQueuedDispatchesInOneCycle() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setBatchSize(7);
        ProcessingJobStorePort jobStore = mock(ProcessingJobStorePort.class);
        when(jobStore.recoverStale(NOW)).thenReturn(List.of());
        when(jobStore.dispatchReadyRetries(NOW, 7)).thenReturn(2);
        when(jobStore.redispatchStaleQueued(NOW, 7)).thenReturn(3);
        CaptureJobRecoveryScheduler scheduler = scheduler(jobStore, mock(IncompleteEventPublications.class), properties);

        scheduler.recoverJobs();

        verify(jobStore).recoverStale(NOW);
        verify(jobStore).dispatchReadyRetries(NOW, 7);
        verify(jobStore).redispatchStaleQueued(NOW, 7);
    }

    @Test
    void recordsRetryAndDeadMetricsFromCommittedStaleRecoveryOutcomes() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        ProcessingJobStorePort jobStore = mock(ProcessingJobStorePort.class);
        CaptureTelemetryPort telemetry = mock(CaptureTelemetryPort.class);
        when(jobStore.recoverStale(NOW)).thenReturn(List.of(
                new RecoveredJobResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        RecoveryOutcome.RETRY_SCHEDULED,
                        TextClassificationFailureClass.SYSTEM_RETRYABLE
                ),
                new RecoveredJobResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        RecoveryOutcome.DEAD,
                        TextClassificationFailureClass.SYSTEM_RETRYABLE
                )
        ));
        CaptureJobRecoveryScheduler scheduler = new CaptureJobRecoveryScheduler(
                jobStore,
                mock(IncompleteEventPublications.class),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                telemetry
        );

        scheduler.recoverJobs();

        verify(telemetry).incrementStaleRecovered(2);
        verify(telemetry).incrementRetry(CaptureTelemetryPort.RetrySource.STALE_RECOVERY);
        verify(telemetry).incrementDead(
                CaptureTelemetryPort.DeadSource.STALE_RECOVERY,
                TextClassificationFailureClass.SYSTEM_RETRYABLE
        );
    }

    @Test
    void outboxRecoveryOnlyResubmitsOldCaptureQueueEvents() {
        CaptureProcessingProperties properties = new CaptureProcessingProperties();
        properties.setOutboxRecoveryAge(Duration.ofSeconds(10));
        ProcessingJobStorePort jobStore = mock(ProcessingJobStorePort.class);
        IncompleteEventPublications publications = mock(IncompleteEventPublications.class);
        CaptureJobRecoveryScheduler scheduler = scheduler(jobStore, publications, properties);

        scheduler.recoverOutbox();

        ArgumentCaptor<Predicate<EventPublication>> filterCaptor = ArgumentCaptor.captor();
        verify(publications).resubmitIncompletePublications(filterCaptor.capture());
        Predicate<EventPublication> filter = filterCaptor.getValue();
        Instant cutoff = NOW.minusSeconds(10);
        assertThat(filter.test(publication(queueEvent(), cutoff.minusMillis(1)))).isTrue();
        assertThat(filter.test(publication(queueEvent(), cutoff))).isFalse();
        assertThat(filter.test(publication(queueEvent(), cutoff.plusMillis(1)))).isFalse();
        assertThat(filter.test(publication("another module event", cutoff.minusSeconds(1)))).isFalse();
    }

    private CaptureJobRecoveryScheduler scheduler(
            ProcessingJobStorePort jobStore,
            IncompleteEventPublications publications,
            CaptureProcessingProperties properties
    ) {
        return new CaptureJobRecoveryScheduler(
                jobStore,
                publications,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(CaptureTelemetryPort.class)
        );
    }

    private EventPublication publication(Object event, Instant publicationDate) {
        EventPublication publication = mock(EventPublication.class);
        when(publication.getEvent()).thenReturn(event);
        when(publication.getPublicationDate()).thenReturn(publicationDate);
        return publication;
    }

    private TextCaptureQueuedEvent queueEvent() {
        return new TextCaptureQueuedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "recovery-test-request"
        );
    }
}

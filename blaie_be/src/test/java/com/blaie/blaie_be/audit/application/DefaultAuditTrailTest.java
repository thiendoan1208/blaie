package com.blaie.blaie_be.audit.application;

import com.blaie.blaie_be.audit.application.port.AuditEventStorePort;
import com.blaie.blaie_be.audit.domain.AuditAccess;
import com.blaie.blaie_be.audit.domain.AuditEvent;
import com.blaie.blaie_be.audit.domain.AuditOutcome;
import com.blaie.blaie_be.core.request.RequestContext;
import com.blaie.blaie_be.core.request.RequestContextHolder;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultAuditTrailTest {
    private static final Instant NOW = Instant.parse("2026-07-17T15:07:35Z");

    @AfterEach
    void clearContext() {
        CurrentUserHolder.clear();
        RequestContextHolder.clear();
    }

    @Test
    void recordsOnlySafeMetadataAndBucketsReadEvents() {
        UUID actorId = UUID.randomUUID();
        AuditEventStorePort store = mock(AuditEventStorePort.class);
        DefaultAuditTrail trail = new DefaultAuditTrail(
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> Duration.ofMinutes(15)
        );
        CurrentUserHolder.set(new CurrentUser(actorId.toString(), false, Set.of()));
        RequestContextHolder.set(new RequestContext("request-123", "GET", "/api/v1/inbox", null));

        trail.record(new AuditAccess("inbox.list", "inbox", null), 200);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(store).append(event.capture());
        assertThat(event.getValue().actorId()).isEqualTo(actorId.toString());
        assertThat(event.getValue().resourceId()).isEqualTo(actorId.toString());
        assertThat(event.getValue().outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getValue().requestId()).isEqualTo("request-123");
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().deduplicationBucket())
                .isEqualTo(Instant.parse("2026-07-17T15:00:00Z"));
    }

    @Test
    void mutationsAreNeverDeduplicated() {
        AuditEventStorePort store = mock(AuditEventStorePort.class);
        DefaultAuditTrail trail = new DefaultAuditTrail(
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> Duration.ofMinutes(15)
        );
        CurrentUserHolder.set(new CurrentUser(UUID.randomUUID().toString(), false, Set.of()));

        trail.record(new AuditAccess("capture.delete", "capture", UUID.randomUUID().toString()), 204);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(store).append(event.capture());
        assertThat(event.getValue().deduplicationBucket()).isNull();
    }

    @Test
    void rejectedReadsAreNeverDeduplicated() {
        AuditEventStorePort store = mock(AuditEventStorePort.class);
        DefaultAuditTrail trail = new DefaultAuditTrail(
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> Duration.ofMinutes(15)
        );
        CurrentUserHolder.set(new CurrentUser(UUID.randomUUID().toString(), false, Set.of()));

        trail.record(new AuditAccess("capture.read", "capture", UUID.randomUUID().toString()), 404);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(store).append(event.capture());
        assertThat(event.getValue().outcome()).isEqualTo(AuditOutcome.NOT_FOUND);
        assertThat(event.getValue().deduplicationBucket()).isNull();
    }
}

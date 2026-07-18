package com.blaie.blaie_be.audit.application;

import com.blaie.blaie_be.audit.application.port.AuditEventStorePort;
import com.blaie.blaie_be.audit.application.port.AuditSettingsPort;
import com.blaie.blaie_be.audit.domain.AuditAccess;
import com.blaie.blaie_be.audit.domain.AuditEvent;
import com.blaie.blaie_be.audit.domain.AuditOutcome;
import com.blaie.blaie_be.core.request.RequestContextHolder;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultAuditTrail implements AuditTrail {
    private final AuditEventStorePort store;
    private final Clock clock;
    private final AuditSettingsPort settings;

    public DefaultAuditTrail(AuditEventStorePort store, Clock clock, AuditSettingsPort settings) {
        this.store = store;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public void record(AuditAccess access, int httpStatus) {
        CurrentUser actor = CurrentUserHolder.current().orElse(null);
        String actorId = actor == null ? "anonymous" : actor.userId();
        Instant occurredAt = clock.instant();
        String resourceId = access.resourceId();
        if (resourceId == null && ("capture.list".equals(access.action()) || "inbox.list".equals(access.action()))) {
            resourceId = actorId;
        }
        AuditOutcome outcome = AuditOutcome.fromHttpStatus(httpStatus);
        store.append(new AuditEvent(
                UUID.randomUUID(),
                actorId,
                actor != null && actor.admin(),
                access.action(),
                access.resourceType(),
                resourceId,
                outcome,
                RequestContextHolder.currentRequestId().orElse("background"),
                occurredAt,
                isRead(access.action()) && outcome == AuditOutcome.SUCCESS ? bucket(occurredAt) : null
        ));
    }

    private boolean isRead(String action) {
        return action.endsWith(".read") || action.endsWith(".list");
    }

    private Instant bucket(Instant value) {
        long seconds = settings.readDeduplicationWindow().toSeconds();
        return Instant.ofEpochSecond(Math.floorDiv(value.getEpochSecond(), seconds) * seconds);
    }
}

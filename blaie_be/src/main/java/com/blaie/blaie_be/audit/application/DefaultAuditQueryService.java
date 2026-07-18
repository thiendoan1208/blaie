package com.blaie.blaie_be.audit.application;

import com.blaie.blaie_be.audit.application.port.AuditEventStorePort;
import com.blaie.blaie_be.audit.application.result.AuditEventPageResult;
import com.blaie.blaie_be.audit.domain.AuditEvent;
import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.core.cursor.SignedCursorCodec;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultAuditQueryService implements AuditQueryService {
    private static final int MAX_LIMIT = 100;
    private static final String CURSOR_AUDIENCE = "admin-audit-events";

    private final AuthorizationService authorizationService;
    private final AuditEventStorePort store;
    private final SignedCursorCodec cursorCodec;

    public DefaultAuditQueryService(
            AuthorizationService authorizationService,
            AuditEventStorePort store,
            SignedCursorCodec cursorCodec
    ) {
        this.authorizationService = authorizationService;
        this.store = store;
        this.cursorCodec = cursorCodec;
    }

    @Override
    public AuditEventPageResult events(String cursor, String limit) {
        authorizationService.require(PermissionAction.ADMIN_AUDIT_READ);
        int safeLimit = requireLimit(limit);
        Cursor decoded = decode(cursor);
        List<AuditEvent> records = store.findPage(
                decoded == null ? null : decoded.occurredAt(),
                decoded == null ? null : decoded.id(),
                safeLimit + 1
        );
        boolean hasMore = records.size() > safeLimit;
        List<AuditEvent> events = hasMore ? records.subList(0, safeLimit) : records;
        String nextCursor = hasMore ? encode(events.getLast()) : null;
        return new AuditEventPageResult(events, nextCursor, hasMore, safeLimit);
    }

    private int requireLimit(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > MAX_LIMIT) throw invalid("limit must be between 1 and " + MAX_LIMIT);
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid("limit must be an integer between 1 and " + MAX_LIMIT);
        }
    }

    private String encode(AuditEvent event) {
        return cursorCodec.encode(CURSOR_AUDIENCE, event.occurredAt() + "|" + event.id());
    }

    private Cursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = cursorCodec.decode(CURSOR_AUDIENCE, value).split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw invalid("cursor is invalid");
        }
    }

    private AppException invalid(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record Cursor(Instant occurredAt, UUID id) {
    }
}

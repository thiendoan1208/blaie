package com.blaie.blaie_be.audit.application;

import com.blaie.blaie_be.audit.application.port.AuditEventStorePort;
import com.blaie.blaie_be.audit.domain.AuditEvent;
import com.blaie.blaie_be.audit.domain.AuditOutcome;
import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.core.cursor.CursorProperties;
import com.blaie.blaie_be.core.cursor.SignedCursorCodec;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAuditQueryServiceTest {
    @Test
    void requiresDedicatedPermissionAndBuildsSignedPages() {
        AuthorizationService authorization = mock(AuthorizationService.class);
        AuditEventStorePort store = mock(AuditEventStorePort.class);
        DefaultAuditQueryService service = new DefaultAuditQueryService(authorization, store, codec());
        AuditEvent first = event(Instant.parse("2026-07-17T12:00:00Z"));
        AuditEvent second = event(Instant.parse("2026-07-17T11:00:00Z"));
        when(store.findPage(isNull(), isNull(), org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of(first, second));

        var page = service.events(null, "1");

        verify(authorization).require(PermissionAction.ADMIN_AUDIT_READ);
        assertThat(page.events()).containsExactly(first);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).startsWith("v1.");
    }

    @Test
    void rejectsTamperedCursorWithGenericValidationError() {
        DefaultAuditQueryService service = new DefaultAuditQueryService(
                mock(AuthorizationService.class),
                mock(AuditEventStorePort.class),
                codec()
        );

        assertThatThrownBy(() -> service.events("v1.unknown.payload.signature", "20"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private AuditEvent event(Instant at) {
        return new AuditEvent(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                true,
                "admin.job.read",
                "processing_job",
                UUID.randomUUID().toString(),
                AuditOutcome.SUCCESS,
                "request-123",
                at,
                at.minusSeconds(at.getEpochSecond() % 900)
        );
    }

    private SignedCursorCodec codec() {
        CursorProperties properties = new CursorProperties();
        properties.setActiveKeyId("v1");
        properties.setActiveSecret("audit-query-test-cursor-secret-at-least-32");
        return new SignedCursorCodec(properties);
    }
}

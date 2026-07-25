package com.blaie.blaie_be.capture.application.admin;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.admin.port.CaptureOutboxAdminQueryPort;
import com.blaie.blaie_be.capture.application.admin.result.AdminOutboxSummaryResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptureOutboxAdminServiceImplTest {
    @Test
    void summaryUsesDedicatedPermissionAndPort() {
        Instant now = Instant.parse("2026-07-25T12:00:00Z");
        CaptureOutboxAdminQueryPort query = mock(CaptureOutboxAdminQueryPort.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        AdminOutboxSummaryResult expected = new AdminOutboxSummaryResult(2, now, 10, null, 3);
        when(query.outboxSummary(now)).thenReturn(expected);
        CaptureOutboxAdminServiceImpl service = new CaptureOutboxAdminServiceImpl(
                query,
                authorization,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThat(service.summary()).isEqualTo(expected);
        verify(authorization).require(PermissionAction.ADMIN_CAPTURE_OUTBOX_READ);
        verify(query).outboxSummary(now);
    }
}

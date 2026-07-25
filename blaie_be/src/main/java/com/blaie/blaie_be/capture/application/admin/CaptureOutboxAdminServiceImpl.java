package com.blaie.blaie_be.capture.application.admin;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.capture.application.admin.port.CaptureOutboxAdminQueryPort;
import com.blaie.blaie_be.capture.application.admin.result.AdminOutboxSummaryResult;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class CaptureOutboxAdminServiceImpl implements CaptureOutboxAdminService {
    private final CaptureOutboxAdminQueryPort outboxQuery;
    private final AuthorizationService authorization;
    private final Clock clock;

    public CaptureOutboxAdminServiceImpl(
            CaptureOutboxAdminQueryPort outboxQuery,
            AuthorizationService authorization,
            Clock clock
    ) {
        this.outboxQuery = outboxQuery;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    public AdminOutboxSummaryResult summary() {
        authorization.require(PermissionAction.ADMIN_CAPTURE_OUTBOX_READ);
        return outboxQuery.outboxSummary(clock.instant());
    }
}

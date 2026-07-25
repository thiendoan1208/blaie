package com.blaie.blaie_be.capture.application.admin.port;

import com.blaie.blaie_be.capture.application.admin.result.AdminOutboxSummaryResult;
import java.time.Instant;

public interface CaptureOutboxAdminQueryPort {
    AdminOutboxSummaryResult outboxSummary(Instant now);
}

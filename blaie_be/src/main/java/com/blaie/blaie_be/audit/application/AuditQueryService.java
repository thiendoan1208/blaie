package com.blaie.blaie_be.audit.application;

import com.blaie.blaie_be.audit.application.result.AuditEventPageResult;

public interface AuditQueryService {
    AuditEventPageResult events(String cursor, String limit);
}

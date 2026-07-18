package com.blaie.blaie_be.audit.application;

import com.blaie.blaie_be.audit.domain.AuditAccess;

public interface AuditTrail {
    void record(AuditAccess access, int httpStatus);
}

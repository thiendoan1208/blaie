package com.blaie.blaie_be.audit.domain;

public record AuditAccess(String action, String resourceType, String resourceId) {
    public AuditAccess {
        require(action, "action", "[a-z0-9_.]{1,80}");
        require(resourceType, "resourceType", "[a-z0-9_]{1,50}");
        if (resourceId != null) {
            require(resourceId, "resourceId", "[A-Za-z0-9._:-]{1,128}");
        }
    }

    private static void require(String value, String field, String pattern) {
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}

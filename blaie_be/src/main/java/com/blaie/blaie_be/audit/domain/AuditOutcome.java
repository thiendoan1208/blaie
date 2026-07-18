package com.blaie.blaie_be.audit.domain;

public enum AuditOutcome {
    SUCCESS("success"),
    DENIED("denied"),
    NOT_FOUND("not_found"),
    REJECTED("rejected"),
    FAILED("failed");

    private final String value;

    AuditOutcome(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AuditOutcome fromHttpStatus(int status) {
        if (status >= 200 && status < 300) return SUCCESS;
        if (status == 401 || status == 403) return DENIED;
        if (status == 404) return NOT_FOUND;
        if (status >= 400 && status < 500) return REJECTED;
        return FAILED;
    }
}

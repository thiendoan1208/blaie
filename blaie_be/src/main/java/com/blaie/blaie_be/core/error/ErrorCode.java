package com.blaie.blaie_be.core.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Session expired"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "Session revoked"),
    TENANT_FORBIDDEN(HttpStatus.FORBIDDEN, "Tenant forbidden"),
    OWNER_REQUIRED(HttpStatus.FORBIDDEN, "Owner required"),
    MEMBERSHIP_REQUIRED(HttpStatus.FORBIDDEN, "Membership required"),
    RESOURCE_NOT_FOUND_OR_FORBIDDEN(HttpStatus.NOT_FOUND, "Resource not found"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

package com.blaie.blaie_be.core.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username already exists"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Google authentication failed"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email not verified"),
    INVALID_EMAIL_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "Invalid email verification token"),
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

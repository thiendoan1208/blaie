package com.blaie.blaie_be.core.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username already exists"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Google authentication failed"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email not verified"),
    INVALID_EMAIL_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "Invalid email verification token"),
    PASSWORD_RESET_INVALID_CODE(HttpStatus.BAD_REQUEST, "Invalid password reset code"),
    PASSWORD_RESET_EXPIRED(HttpStatus.BAD_REQUEST, "Password reset code expired"),
    PASSWORD_RESET_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many password reset attempts"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    EMAIL_VERIFICATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many email verification requests"),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Session expired"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "Session revoked"),
    OWNER_REQUIRED(HttpStatus.FORBIDDEN, "Owner required"),
    RESOURCE_NOT_FOUND_OR_FORBIDDEN(HttpStatus.NOT_FOUND, "Resource not found"),
    CAPTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "Capture not found"),
    CAPTURE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Inbox item not found"),
    CAPTURE_NOT_RETRYABLE(HttpStatus.CONFLICT, "Capture cannot be retried"),
    CAPTURE_SENSITIVE_CONTENT(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Capture contains sensitive content that cannot be stored"
    ),
    PROCESSING_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "Processing job not found"),
    PROCESSING_JOB_REQUEUE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "Processing job cannot be requeued"
    ),
    PROCESSING_JOB_MARK_DEAD_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "Processing job cannot be marked dead"
    ),
    TOO_MANY_ACTIVE_JOBS(HttpStatus.TOO_MANY_REQUESTS, "Too many active capture jobs"),
    CAPTURE_PROCESSING_OVERLOADED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Capture processing is temporarily overloaded"
    ),
    CAPTURE_PROCESSING_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Capture processing is temporarily unavailable"
    ),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required"),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Idempotency-Key header must be a UUID"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key was reused with another request"),
    AUDIO_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Audio file is required"),
    AUDIO_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, "Audio file is empty"),
    AUDIO_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Audio file exceeds the 10 MB limit"),
    AUDIO_TYPE_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Audio type is not supported"),
    IMAGE_CAPTURE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "Image capture is temporarily unavailable"),
    IMAGE_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Image file is required"),
    IMAGE_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, "Image file is empty"),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Image file exceeds the configured limit"),
    IMAGE_TYPE_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image type is not supported"),
    IMAGE_DIMENSIONS_UNSUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT, "Image dimensions are not supported"),
    IMAGE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "Image file is invalid"),
    IMAGE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is temporarily unavailable"),
    TRANSCRIPTION_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, "No speech was detected"),
    TRANSCRIPTION_TOO_LONG(HttpStatus.UNPROCESSABLE_CONTENT, "Transcript exceeds the capture text limit"),
    TRANSCRIPTION_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Transcription is not configured"),
    TRANSCRIPTION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Transcription is temporarily unavailable"),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI classification is temporarily unavailable"),
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

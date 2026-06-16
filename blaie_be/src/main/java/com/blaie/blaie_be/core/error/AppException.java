package com.blaie.blaie_be.core.error;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, List<String>> fieldErrors;

    public AppException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public AppException(ErrorCode errorCode, String message, Map<String, List<String>> fieldErrors) {
        super(message == null || message.isBlank() ? errorCode.defaultMessage() : message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? Map.of() : Collections.unmodifiableMap(fieldErrors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, List<String>> fieldErrors() {
        return fieldErrors;
    }
}

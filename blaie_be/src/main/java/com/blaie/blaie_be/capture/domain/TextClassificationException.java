package com.blaie.blaie_be.capture.domain;

public class TextClassificationException extends RuntimeException {
    private final String failureCode;
    private final boolean retryable;

    public TextClassificationException(String failureCode, String message) {
        this(failureCode, message, true, null);
    }

    public TextClassificationException(String failureCode, String message, Throwable cause) {
        this(failureCode, message, true, cause);
    }

    public TextClassificationException(String failureCode, String message, boolean retryable) {
        this(failureCode, message, retryable, null);
    }

    public TextClassificationException(
            String failureCode,
            String message,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public String failureCode() {
        return failureCode;
    }

    public boolean retryable() {
        return retryable;
    }
}

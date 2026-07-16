package com.blaie.blaie_be.capture.domain;

public class TextClassificationException extends RuntimeException {
    private final String failureCode;

    public TextClassificationException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public TextClassificationException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}

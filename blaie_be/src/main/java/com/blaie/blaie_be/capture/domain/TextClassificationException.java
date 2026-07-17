package com.blaie.blaie_be.capture.domain;

import java.util.Objects;

public class TextClassificationException extends RuntimeException {
    private final String failureCode;
    private final TextClassificationFailureClass failureClass;

    public TextClassificationException(
            String failureCode,
            String message,
            TextClassificationFailureClass failureClass
    ) {
        this(failureCode, message, failureClass, null);
    }

    public TextClassificationException(
            String failureCode,
            String message,
            TextClassificationFailureClass failureClass,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
        this.failureClass = Objects.requireNonNull(failureClass, "failureClass");
    }

    public String failureCode() {
        return failureCode;
    }

    public TextClassificationFailureClass failureClass() {
        return failureClass;
    }
}

package com.blaie.blaie_be.capture.domain;

import java.util.Objects;

public class CaptureAnalysisException extends RuntimeException {
    private final String failureCode;
    private final CaptureFailureClass failureClass;

    public CaptureAnalysisException(
            String failureCode,
            String message,
            CaptureFailureClass failureClass
    ) {
        this(failureCode, message, failureClass, null);
    }

    public CaptureAnalysisException(
            String failureCode,
            String message,
            CaptureFailureClass failureClass,
            Throwable cause
    ) {
        super(message, cause);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
        this.failureClass = Objects.requireNonNull(failureClass, "failureClass");
    }

    public String failureCode() {
        return failureCode;
    }

    public CaptureFailureClass failureClass() {
        return failureClass;
    }
}

package com.blaie.blaie_be.capture.domain;

public enum ProcessingStatus {
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    ProcessingStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ProcessingStatus fromValue(String value) {
        for (ProcessingStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported processing status");
    }
}

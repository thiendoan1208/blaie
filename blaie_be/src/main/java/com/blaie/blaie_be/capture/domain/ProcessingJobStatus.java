package com.blaie.blaie_be.capture.domain;

public enum ProcessingJobStatus {
    QUEUED("queued"),
    PROCESSING("processing"),
    RETRY_WAIT("retry_wait"),
    COMPLETED("completed"),
    DEAD("dead");

    private final String value;

    ProcessingJobStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ProcessingJobStatus fromValue(String value) {
        for (ProcessingJobStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported processing job status");
    }
}

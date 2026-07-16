package com.blaie.blaie_be.capture.domain;

import java.util.Arrays;

public enum CaptureCategory {
    TASK("task"),
    CALENDAR_EVENT("calendar_event"),
    REMINDER("reminder"),
    INFORMATION("information");

    private final String value;

    CaptureCategory(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CaptureCategory fromValue(String value) {
        return Arrays.stream(values())
                .filter(category -> category.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported capture category"));
    }
}

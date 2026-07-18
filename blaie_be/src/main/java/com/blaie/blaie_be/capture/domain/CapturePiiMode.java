package com.blaie.blaie_be.capture.domain;

import java.util.Arrays;

public enum CapturePiiMode {
    MASK_STRUCTURED("mask_structured"),
    ALLOW("allow");

    private final String value;

    CapturePiiMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CapturePiiMode fromValue(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown capture PII mode: " + value));
    }
}

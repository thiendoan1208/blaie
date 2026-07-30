package com.blaie.blaie_be.capture.domain;

import java.util.Arrays;

public enum CaptureInputType {
    TEXT("text"),
    IMAGE("image");

    private final String value;

    CaptureInputType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CaptureInputType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown capture input type: " + value));
    }
}

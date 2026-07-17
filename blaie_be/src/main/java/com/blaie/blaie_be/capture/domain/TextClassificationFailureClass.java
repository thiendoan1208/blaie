package com.blaie.blaie_be.capture.domain;

import java.util.Arrays;

public enum TextClassificationFailureClass {
    CONTENT_TERMINAL("content_terminal", false, false, false),
    PROVIDER_TERMINAL("provider_terminal", false, true, true),
    PROVIDER_RETRYABLE("provider_retryable", true, true, true),
    SYSTEM_RETRYABLE("system_retryable", true, false, true);

    private final String value;
    private final boolean automaticRetryAllowed;
    private final boolean providerFallbackAllowed;
    private final boolean manualRetryAllowed;

    TextClassificationFailureClass(
            String value,
            boolean automaticRetryAllowed,
            boolean providerFallbackAllowed,
            boolean manualRetryAllowed
    ) {
        this.value = value;
        this.automaticRetryAllowed = automaticRetryAllowed;
        this.providerFallbackAllowed = providerFallbackAllowed;
        this.manualRetryAllowed = manualRetryAllowed;
    }

    public String value() {
        return value;
    }

    public boolean automaticRetryAllowed() {
        return automaticRetryAllowed;
    }

    public boolean providerFallbackAllowed() {
        return providerFallbackAllowed;
    }

    public boolean manualRetryAllowed() {
        return manualRetryAllowed;
    }

    public static TextClassificationFailureClass fromValue(String value) {
        return Arrays.stream(values())
                .filter(failureClass -> failureClass.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown text classification failure class: " + value
                ));
    }
}

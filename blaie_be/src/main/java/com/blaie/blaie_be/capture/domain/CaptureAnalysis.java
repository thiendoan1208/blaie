package com.blaie.blaie_be.capture.domain;

import java.util.List;

public record CaptureAnalysis(
        List<ClassifiedTextItem> items,
        String provider,
        String model,
        String promptVersion
) {
    public CaptureAnalysis {
        items = List.copyOf(items);
        if (items.size() > 32) {
            throw new IllegalArgumentException("items must not exceed 32");
        }
        requireValue(provider, "provider");
        requireValue(model, "model");
        requireValue(promptVersion, "promptVersion");
    }

    private static void requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}

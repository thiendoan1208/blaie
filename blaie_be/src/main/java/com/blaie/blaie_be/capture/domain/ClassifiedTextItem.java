package com.blaie.blaie_be.capture.domain;

import java.util.Objects;

public record ClassifiedTextItem(
        String originalText,
        CaptureCategory category
) {
    public ClassifiedTextItem {
        if (originalText == null || originalText.isBlank()) {
            throw new IllegalArgumentException("originalText is required");
        }
        Objects.requireNonNull(category, "category is required");
    }
}

package com.blaie.blaie_be.capture.application.result;

import java.util.UUID;

public record CaptureAssetResult(
        UUID id,
        String type,
        String contentType,
        long sizeBytes,
        int width,
        int height
) {
}

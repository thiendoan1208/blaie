package com.blaie.blaie_be.capture.application.port;

import java.util.UUID;

public record CaptureAssetDraft(
        UUID id,
        int position,
        String storageProvider,
        String objectKey,
        String contentType,
        long sizeBytes,
        String sha256,
        int width,
        int height
) {
}

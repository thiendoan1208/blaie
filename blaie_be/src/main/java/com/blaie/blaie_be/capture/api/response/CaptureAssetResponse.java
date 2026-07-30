package com.blaie.blaie_be.capture.api.response;

import com.blaie.blaie_be.capture.application.result.CaptureAssetResult;
import java.util.UUID;

public record CaptureAssetResponse(
        UUID id,
        String type,
        String contentType,
        long sizeBytes,
        int width,
        int height,
        String contentUrl
) {
    static CaptureAssetResponse from(UUID captureId, CaptureAssetResult asset) {
        return new CaptureAssetResponse(
                asset.id(),
                asset.type(),
                asset.contentType(),
                asset.sizeBytes(),
                asset.width(),
                asset.height(),
                "/api/v1/captures/" + captureId + "/assets/" + asset.id() + "/content"
        );
    }
}

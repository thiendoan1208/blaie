package com.blaie.blaie_be.capture.application.result;

import java.util.UUID;

public record OwnedCaptureAssetResult(
        UUID id,
        UUID captureId,
        String objectKey
) {
}

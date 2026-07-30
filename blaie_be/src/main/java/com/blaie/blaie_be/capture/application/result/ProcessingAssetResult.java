package com.blaie.blaie_be.capture.application.result;

import java.util.UUID;

public record ProcessingAssetResult(
        UUID id,
        String objectKey,
        String contentType
) {
}

package com.blaie.blaie_be.capture.application.port;

import java.time.Duration;

public interface ImageCaptureSettingsPort {
    boolean enabled();

    default Duration signedUrlTtl() {
        return Duration.ofMinutes(2);
    }
}

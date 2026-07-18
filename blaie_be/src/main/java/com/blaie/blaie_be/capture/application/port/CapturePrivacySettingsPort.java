package com.blaie.blaie_be.capture.application.port;

import com.blaie.blaie_be.capture.domain.CapturePiiMode;

public interface CapturePrivacySettingsPort {
    CapturePiiMode piiMode();
}

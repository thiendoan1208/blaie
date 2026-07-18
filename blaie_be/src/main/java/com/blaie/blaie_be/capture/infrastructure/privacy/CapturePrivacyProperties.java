package com.blaie.blaie_be.capture.infrastructure.privacy;

import com.blaie.blaie_be.capture.application.port.CapturePrivacySettingsPort;
import com.blaie.blaie_be.capture.domain.CapturePiiMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.capture.privacy")
public class CapturePrivacyProperties implements CapturePrivacySettingsPort {
    @NotBlank
    private String piiMode = CapturePiiMode.MASK_STRUCTURED.value();

    @Override
    public CapturePiiMode piiMode() {
        return CapturePiiMode.fromValue(piiMode);
    }

    public void setPiiMode(String piiMode) {
        this.piiMode = piiMode;
    }

    @AssertTrue(message = "Capture PII mode must be mask_structured or allow")
    public boolean isPiiModeValid() {
        try {
            piiMode();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}

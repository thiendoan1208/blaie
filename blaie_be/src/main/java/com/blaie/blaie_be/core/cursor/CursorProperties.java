package com.blaie.blaie_be.core.cursor;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.security.cursor")
public class CursorProperties {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,32}")
    private String activeKeyId = "v1";

    @NotBlank
    @Size(min = 32, message = "Cursor HMAC secret must contain at least 32 characters")
    private String activeSecret;

    private String previousKeyId;
    private String previousSecret;

    public String activeKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public String activeSecret() {
        return activeSecret;
    }

    public void setActiveSecret(String activeSecret) {
        this.activeSecret = activeSecret;
    }

    public String previousKeyId() {
        return previousKeyId;
    }

    public void setPreviousKeyId(String previousKeyId) {
        this.previousKeyId = previousKeyId;
    }

    public String previousSecret() {
        return previousSecret;
    }

    public void setPreviousSecret(String previousSecret) {
        this.previousSecret = previousSecret;
    }

    @AssertTrue(message = "Previous cursor key id and secret must be configured together and differ from the active key")
    public boolean isPreviousKeyConfigurationValid() {
        boolean keyPresent = hasText(previousKeyId);
        boolean secretPresent = hasText(previousSecret);
        if (keyPresent != secretPresent) {
            return false;
        }
        if (!keyPresent) {
            return true;
        }
        return previousKeyId.matches("[A-Za-z0-9_-]{1,32}")
                && previousSecret.length() >= 32
                && !previousKeyId.equals(activeKeyId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

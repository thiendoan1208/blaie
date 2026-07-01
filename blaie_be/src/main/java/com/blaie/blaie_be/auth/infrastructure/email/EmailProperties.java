package com.blaie.blaie_be.auth.infrastructure.email;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.email")
public class EmailProperties {
    @NotBlank
    private String provider;

    @NotBlank
    private String from;

    @NotBlank
    private String webBaseUrl;

    @NotBlank
    private String apiBaseUrl;

    @NotNull
    private Duration verificationTtl;

    @NotNull
    private Duration passwordResetTtl = Duration.ofMinutes(15);

    private String resendApiKey;

    public String provider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String from() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String webBaseUrl() {
        return stripTrailingSlash(webBaseUrl);
    }

    public void setWebBaseUrl(String webBaseUrl) {
        this.webBaseUrl = webBaseUrl;
    }

    public String apiBaseUrl() {
        return stripTrailingSlash(apiBaseUrl);
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public Duration verificationTtl() {
        return verificationTtl;
    }

    public void setVerificationTtl(Duration verificationTtl) {
        this.verificationTtl = verificationTtl;
    }

    public Duration passwordResetTtl() {
        return passwordResetTtl;
    }

    public void setPasswordResetTtl(Duration passwordResetTtl) {
        this.passwordResetTtl = passwordResetTtl;
    }

    public String resendApiKey() {
        return resendApiKey;
    }

    public void setResendApiKey(String resendApiKey) {
        this.resendApiKey = resendApiKey;
    }

    @AssertTrue(message = "Email verification TTL must be positive")
    public boolean isVerificationTtlValid() {
        return verificationTtl != null && !verificationTtl.isZero() && !verificationTtl.isNegative();
    }

    @AssertTrue(message = "Password reset TTL must be positive")
    public boolean isPasswordResetTtlValid() {
        return passwordResetTtl != null && !passwordResetTtl.isZero() && !passwordResetTtl.isNegative();
    }

    @AssertTrue(message = "Resend API key is required when blaie.email.provider=resend")
    public boolean isResendApiKeyConfigured() {
        return !"resend".equalsIgnoreCase(provider)
                || (resendApiKey != null && !resendApiKey.isBlank());
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

}

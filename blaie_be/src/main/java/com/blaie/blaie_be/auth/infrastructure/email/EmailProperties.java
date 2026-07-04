package com.blaie.blaie_be.auth.infrastructure.email;

import com.blaie.blaie_be.auth.application.port.EmailSettingsPort;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.email")
public class EmailProperties implements EmailSettingsPort {
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

    @NotNull
    private Duration verificationResendCooldown = Duration.ofSeconds(60);

    @NotNull
    private Duration verificationResendQuotaWindow = Duration.ofHours(24);

    @Min(1)
    private long verificationResendQuotaLimit = 5;

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

    public Duration verificationResendCooldown() {
        return verificationResendCooldown;
    }

    public void setVerificationResendCooldown(Duration verificationResendCooldown) {
        this.verificationResendCooldown = verificationResendCooldown;
    }

    public Duration verificationResendQuotaWindow() {
        return verificationResendQuotaWindow;
    }

    public void setVerificationResendQuotaWindow(Duration verificationResendQuotaWindow) {
        this.verificationResendQuotaWindow = verificationResendQuotaWindow;
    }

    public long verificationResendQuotaLimit() {
        return verificationResendQuotaLimit;
    }

    public void setVerificationResendQuotaLimit(long verificationResendQuotaLimit) {
        this.verificationResendQuotaLimit = verificationResendQuotaLimit;
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

    @AssertTrue(message = "Email verification resend cooldown must be positive")
    public boolean isVerificationResendCooldownValid() {
        return verificationResendCooldown != null
                && !verificationResendCooldown.isZero()
                && !verificationResendCooldown.isNegative();
    }

    @AssertTrue(message = "Email verification resend quota window must be positive")
    public boolean isVerificationResendQuotaWindowValid() {
        return verificationResendQuotaWindow != null
                && !verificationResendQuotaWindow.isZero()
                && !verificationResendQuotaWindow.isNegative();
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

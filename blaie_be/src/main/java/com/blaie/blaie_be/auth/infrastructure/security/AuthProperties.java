package com.blaie.blaie_be.auth.infrastructure.security;

import com.blaie.blaie_be.auth.application.port.AuthTokenSettingsPort;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.auth")
public class AuthProperties implements AuthTokenSettingsPort {
    private static final java.util.regex.Pattern KEY_ID_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @NotBlank
    @Size(min = 32, message = "Access token secret must contain at least 32 characters")
    private String accessTokenSecret;

    @NotBlank
    @Size(max = 255)
    private String accessTokenIssuer = "blaie-api";

    @NotBlank
    @Size(max = 255)
    private String accessTokenAudience = "blaie-clients";

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$")
    private String accessTokenActiveKeyId = "v1";

    private String accessTokenPreviousKeyId;

    private String accessTokenPreviousSecret;

    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    @NotNull
    private Duration refreshTokenTtl = Duration.ofDays(30);

    private boolean cookieSecure = false;

    @NotBlank
    @Pattern(regexp = "^(Lax|Strict|None)$")
    private String cookieSameSite = "Lax";

    public String accessTokenSecret() {
        return accessTokenSecret;
    }

    public void setAccessTokenSecret(String accessTokenSecret) {
        this.accessTokenSecret = accessTokenSecret;
    }

    public String accessTokenIssuer() {
        return accessTokenIssuer;
    }

    public void setAccessTokenIssuer(String accessTokenIssuer) {
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public String accessTokenAudience() {
        return accessTokenAudience;
    }

    public void setAccessTokenAudience(String accessTokenAudience) {
        this.accessTokenAudience = accessTokenAudience;
    }

    public String accessTokenActiveKeyId() {
        return accessTokenActiveKeyId;
    }

    public void setAccessTokenActiveKeyId(String accessTokenActiveKeyId) {
        this.accessTokenActiveKeyId = accessTokenActiveKeyId;
    }

    public void setAccessTokenPreviousKeyId(String accessTokenPreviousKeyId) {
        this.accessTokenPreviousKeyId = blankToNull(accessTokenPreviousKeyId);
    }

    public void setAccessTokenPreviousSecret(String accessTokenPreviousSecret) {
        this.accessTokenPreviousSecret = blankToNull(accessTokenPreviousSecret);
    }

    public Optional<String> accessTokenSecretFor(String keyId) {
        if (accessTokenActiveKeyId.equals(keyId)) {
            return Optional.of(accessTokenSecret);
        }
        if (accessTokenPreviousKeyId != null && accessTokenPreviousKeyId.equals(keyId)) {
            return Optional.of(accessTokenPreviousSecret);
        }
        return Optional.empty();
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public boolean cookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String cookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }

    @AssertTrue(message = "Access token TTL must be positive and shorter than refresh token TTL")
    public boolean isAccessTokenTtlValid() {
        return accessTokenTtl != null
                && refreshTokenTtl != null
                && !accessTokenTtl.isZero()
                && !accessTokenTtl.isNegative()
                && accessTokenTtl.compareTo(refreshTokenTtl) < 0;
    }

    @AssertTrue(message = "Refresh token TTL must be positive")
    public boolean isRefreshTokenTtlValid() {
        return refreshTokenTtl != null && !refreshTokenTtl.isZero() && !refreshTokenTtl.isNegative();
    }

    @AssertTrue(message = "SameSite=None cookies must also be Secure")
    public boolean isSameSiteCompatibleWithSecureCookie() {
        return !"None".equals(cookieSameSite) || cookieSecure;
    }

    @AssertTrue(message = "Previous access token key ID and secret must be configured together and differ from the active key")
    public boolean isPreviousAccessTokenKeyValid() {
        if (accessTokenPreviousKeyId == null && accessTokenPreviousSecret == null) {
            return true;
        }
        return accessTokenPreviousKeyId != null
                && KEY_ID_PATTERN.matcher(accessTokenPreviousKeyId).matches()
                && accessTokenPreviousSecret != null
                && accessTokenPreviousSecret.length() >= 32
                && !accessTokenActiveKeyId.equals(accessTokenPreviousKeyId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

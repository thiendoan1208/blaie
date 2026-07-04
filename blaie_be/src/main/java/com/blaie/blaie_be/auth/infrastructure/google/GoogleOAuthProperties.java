package com.blaie.blaie_be.auth.infrastructure.google;

import com.blaie.blaie_be.auth.application.port.GoogleOAuthSettingsPort;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.google.oauth")
public class GoogleOAuthProperties implements GoogleOAuthSettingsPort {
    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String redirectUri;

    @NotBlank
    private String webBaseUrl;

    @NotBlank
    private String authorizationUri;

    @NotBlank
    private String tokenUri;

    @NotBlank
    private String userInfoUri;

    @NotNull
    private Duration stateTtl = Duration.ofMinutes(10);

    public String clientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public URI redirectUri() {
        return URI.create(stripTrailingSlash(redirectUri));
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String webBaseUrl() {
        return stripTrailingSlash(webBaseUrl);
    }

    public void setWebBaseUrl(String webBaseUrl) {
        this.webBaseUrl = webBaseUrl;
    }

    public URI authorizationUri() {
        return URI.create(authorizationUri);
    }

    public void setAuthorizationUri(String authorizationUri) {
        this.authorizationUri = authorizationUri;
    }

    public URI tokenUri() {
        return URI.create(tokenUri);
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public URI userInfoUri() {
        return URI.create(userInfoUri);
    }

    public void setUserInfoUri(String userInfoUri) {
        this.userInfoUri = userInfoUri;
    }

    public Duration stateTtl() {
        return stateTtl;
    }

    public void setStateTtl(Duration stateTtl) {
        this.stateTtl = stateTtl;
    }

    @AssertTrue(message = "Google OAuth state TTL must be positive")
    public boolean isStateTtlValid() {
        return stateTtl != null && !stateTtl.isZero() && !stateTtl.isNegative();
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

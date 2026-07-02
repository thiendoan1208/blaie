package com.blaie.blaie_be.core.ratelimit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "blaie.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private boolean failOpen = true;
    private boolean useForwardedHeaders = false;

    @NotBlank
    private String keyPrefix = "blaie:rl";

    @Valid
    private RateLimitPolicy authLogin = new RateLimitPolicy(
            new RateLimitWindow(5, Duration.ofMinutes(1)),
            new RateLimitWindow(20, Duration.ofMinutes(15))
    );

    @Valid
    private RateLimitPolicy authRegister = new RateLimitPolicy(
            new RateLimitWindow(3, Duration.ofMinutes(10)),
            new RateLimitWindow(20, Duration.ofHours(1))
    );

    @Valid
    private RateLimitPolicy passwordResetRequest = new RateLimitPolicy(
            new RateLimitWindow(3, Duration.ofMinutes(10)),
            new RateLimitWindow(10, Duration.ofHours(1))
    );

    @Valid
    private RateLimitPolicy passwordResetConfirm = new RateLimitPolicy(
            new RateLimitWindow(10, Duration.ofMinutes(10))
    );

    @Valid
    private RateLimitPolicy emailVerification = new RateLimitPolicy(
            new RateLimitWindow(5, Duration.ofMinutes(10))
    );

    @Valid
    private RateLimitPolicy authRefresh = new RateLimitPolicy(
            new RateLimitWindow(30, Duration.ofMinutes(1))
    );

    @Valid
    private RateLimitPolicy authLogout = new RateLimitPolicy(
            new RateLimitWindow(30, Duration.ofMinutes(1))
    );

    @Valid
    private RateLimitPolicy csrf = new RateLimitPolicy(
            new RateLimitWindow(120, Duration.ofMinutes(1))
    );

    @Valid
    private RateLimitPolicy googleStart = new RateLimitPolicy(
            new RateLimitWindow(20, Duration.ofMinutes(10))
    );

    @Valid
    private RateLimitPolicy googleCallback = new RateLimitPolicy(
            new RateLimitWindow(60, Duration.ofMinutes(10))
    );

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean failOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public boolean useForwardedHeaders() {
        return useForwardedHeaders;
    }

    public void setUseForwardedHeaders(boolean useForwardedHeaders) {
        this.useForwardedHeaders = useForwardedHeaders;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public RateLimitPolicy authLogin() {
        return authLogin;
    }

    public void setAuthLogin(RateLimitPolicy authLogin) {
        this.authLogin = authLogin;
    }

    public RateLimitPolicy authRegister() {
        return authRegister;
    }

    public void setAuthRegister(RateLimitPolicy authRegister) {
        this.authRegister = authRegister;
    }

    public RateLimitPolicy passwordResetRequest() {
        return passwordResetRequest;
    }

    public void setPasswordResetRequest(RateLimitPolicy passwordResetRequest) {
        this.passwordResetRequest = passwordResetRequest;
    }

    public RateLimitPolicy passwordResetConfirm() {
        return passwordResetConfirm;
    }

    public void setPasswordResetConfirm(RateLimitPolicy passwordResetConfirm) {
        this.passwordResetConfirm = passwordResetConfirm;
    }

    public RateLimitPolicy emailVerification() {
        return emailVerification;
    }

    public void setEmailVerification(RateLimitPolicy emailVerification) {
        this.emailVerification = emailVerification;
    }

    public RateLimitPolicy authRefresh() {
        return authRefresh;
    }

    public void setAuthRefresh(RateLimitPolicy authRefresh) {
        this.authRefresh = authRefresh;
    }

    public RateLimitPolicy authLogout() {
        return authLogout;
    }

    public void setAuthLogout(RateLimitPolicy authLogout) {
        this.authLogout = authLogout;
    }

    public RateLimitPolicy csrf() {
        return csrf;
    }

    public void setCsrf(RateLimitPolicy csrf) {
        this.csrf = csrf;
    }

    public RateLimitPolicy googleStart() {
        return googleStart;
    }

    public void setGoogleStart(RateLimitPolicy googleStart) {
        this.googleStart = googleStart;
    }

    public RateLimitPolicy googleCallback() {
        return googleCallback;
    }

    public void setGoogleCallback(RateLimitPolicy googleCallback) {
        this.googleCallback = googleCallback;
    }
}

package com.blaie.blaie_be.core.ratelimit.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public class RateLimitWindow {
    @Min(1)
    private int permitLimit;

    @NotNull
    private Duration window;

    public RateLimitWindow() {
    }

    public RateLimitWindow(int permitLimit, Duration window) {
        this.permitLimit = permitLimit;
        this.window = window;
    }

    public int permitLimit() {
        return permitLimit;
    }

    public void setPermitLimit(int permitLimit) {
        this.permitLimit = permitLimit;
    }

    public Duration window() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }
}

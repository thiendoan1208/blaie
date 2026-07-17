package com.blaie.blaie_be.core.ratelimit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RateLimitPolicy {
    private boolean enabled = true;
    private Boolean failOpen;

    @NotEmpty
    private List<@Valid RateLimitWindow> windows = new ArrayList<>();

    public RateLimitPolicy() {
    }

    public RateLimitPolicy(RateLimitWindow... windows) {
        this.windows = new ArrayList<>(List.of(windows));
    }

    public static RateLimitPolicy of(int permitLimit, Duration window) {
        return new RateLimitPolicy(new RateLimitWindow(permitLimit, window));
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean failOpen() {
        return failOpen;
    }

    public void setFailOpen(Boolean failOpen) {
        this.failOpen = failOpen;
    }

    public List<RateLimitWindow> windows() {
        return List.copyOf(windows);
    }

    public void setWindows(List<RateLimitWindow> windows) {
        this.windows = windows == null ? new ArrayList<>() : new ArrayList<>(windows);
    }
}

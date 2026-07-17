package com.blaie.blaie_be.core.ratelimit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RateLimitPolicy {
    private boolean enabled = true;
    private Boolean failOpen;

    @NotEmpty
    private List<@NotNull @Valid RateLimitWindow> windows = new ArrayList<>();

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

    @AssertTrue(message = "Rate limit windows must use unique whole-second durations")
    public boolean isWindowDurationUnique() {
        Set<Long> durations = new HashSet<>();
        for (RateLimitWindow rateLimitWindow : windows) {
            if (rateLimitWindow == null || rateLimitWindow.window() == null) {
                continue;
            }
            if (!durations.add(rateLimitWindow.window().toSeconds())) {
                return false;
            }
        }
        return true;
    }
}

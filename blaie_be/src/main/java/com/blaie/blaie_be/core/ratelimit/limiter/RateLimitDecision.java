package com.blaie.blaie_be.core.ratelimit.limiter;

import java.time.Duration;

public record RateLimitDecision(
        boolean allowed,
        String policyName,
        Duration retryAfter
) {
    public static RateLimitDecision allowed(String policyName) {
        return new RateLimitDecision(true, policyName, Duration.ZERO);
    }

    public static RateLimitDecision denied(String policyName, Duration retryAfter) {
        return new RateLimitDecision(false, policyName, retryAfter);
    }
}

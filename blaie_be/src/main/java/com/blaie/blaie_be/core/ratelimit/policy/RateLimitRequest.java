package com.blaie.blaie_be.core.ratelimit.policy;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitWindow;
import java.util.List;

public record RateLimitRequest(
        String policyName,
        String subject,
        List<RateLimitWindow> windows,
        Boolean failOpen
) {
    public RateLimitRequest(String policyName, String subject, List<RateLimitWindow> windows) {
        this(policyName, subject, windows, null);
    }
}

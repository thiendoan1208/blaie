package com.blaie.blaie_be.core.ratelimit.policy;

import com.blaie.blaie_be.core.ratelimit.config.RateLimitWindow;
import java.util.List;

public record RateLimitRequest(
        String policyName,
        String subject,
        List<RateLimitWindow> windows
) {
}

package com.blaie.blaie_be.core.ratelimit.limiter;

import com.blaie.blaie_be.core.ratelimit.policy.RateLimitRequest;

public interface RateLimiter {
    RateLimitDecision check(RateLimitRequest request);
}

package com.blaie.blaie_be.core.ratelimit.limiter;

public class RateLimitBackendUnavailableException extends RuntimeException {
    public RateLimitBackendUnavailableException(Throwable cause) {
        super("Rate limit backend is unavailable", cause);
    }
}

package com.blaie.blaie_be.core.error;

import java.time.Duration;

public class RateLimitedException extends AppException {
    private final Duration retryAfter;

    public RateLimitedException(ErrorCode errorCode, String message, Duration retryAfter) {
        super(errorCode, message);
        this.retryAfter = retryAfter == null || retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}

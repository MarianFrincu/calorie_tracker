package com.calorietracker.aiservice.ratelimit;

import java.time.Duration;

/** The caller used up their AI quota; mapped to 429 + Retry-After. */
public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("AI rate limit exceeded");
        this.retryAfter = retryAfter;
    }

    /** Whole seconds until the next call can succeed, at least 1. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}

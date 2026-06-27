package com.soham.ratelimiter.core;

public record RateLimitResult(boolean permitted, long retryAfterSeconds) {

    public static RateLimitResult permit() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult deny(long retryAfterSeconds) {
        return new RateLimitResult(false, Math.max(1, retryAfterSeconds));
    }
}

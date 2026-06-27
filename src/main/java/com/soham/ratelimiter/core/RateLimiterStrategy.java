package com.soham.ratelimiter.core;

/**
 * Common interface for rate-limiting strategies.
 *
 * Implementations: in-memory token bucket, Redis token bucket,
 * in-memory sliding window, Redis sliding window.
 */
public interface RateLimiterStrategy {
    RateLimitResult tryConsume(String key);
}

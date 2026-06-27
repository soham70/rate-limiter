package com.soham.ratelimiter.core;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, single-instance implementation of RateLimiterStrategy.
 * Keeps one TokenBucket per key (e.g. per IP address or API key).
 */
public class InMemoryTokenBucketLimiter implements RateLimiterStrategy {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final double refillRatePerSecond;

    public InMemoryTokenBucketLimiter(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    @Override
    public RateLimitResult tryConsume(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(capacity, refillRatePerSecond));
        if (bucket.tryConsume()) {
            return RateLimitResult.permit();
        }
        long retryAfter = (long) Math.ceil(1.0 / refillRatePerSecond);
        return RateLimitResult.deny(retryAfter);
    }
}

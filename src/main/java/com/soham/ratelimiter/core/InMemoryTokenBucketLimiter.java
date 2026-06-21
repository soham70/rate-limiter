package com.soham.ratelimiter.core;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, single-instance implementation of RateLimiterStrategy.
 * Keeps one TokenBucket per key (e.g. per IP address or API key).
 *
 * LIMITATION (call this out explicitly in interviews/README):
 * this only works correctly for a single application instance. If you run
 * 3 replicas behind a load balancer, each replica has its own map, so a
 * client could get 3x the intended limit. That's exactly the problem
 * DAY 3 (Redis-backed bucket) solves — Redis becomes the single shared
 * source of truth for token counts across all instances.
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
    public boolean tryConsume(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(capacity, refillRatePerSecond));
        return bucket.tryConsume();
    }
}

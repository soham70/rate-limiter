package com.soham.ratelimiter.core;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter backed by an in-memory log of request timestamps.
 *
 * More precise than fixed-window counters (no boundary burst problem) but
 * uses O(window request count) memory per key.
 */
public class InMemorySlidingWindowRateLimiter implements RateLimiterStrategy {

    private final ConcurrentHashMap<String, ArrayDeque<Long>> requestLog = new ConcurrentHashMap<>();
    private final long maxRequests;
    private final long windowMillis;

    public InMemorySlidingWindowRateLimiter(long maxRequests, long windowSeconds) {
        if (maxRequests <= 0) throw new IllegalArgumentException("maxRequests must be > 0");
        if (windowSeconds <= 0) throw new IllegalArgumentException("windowSeconds must be > 0");
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    public RateLimitResult tryConsume(String key) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> window = requestLog.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (window) {
            evictExpired(window, now);

            if (window.size() < maxRequests) {
                window.addLast(now);
                return RateLimitResult.permit();
            }

            long oldest = window.peekFirst();
            long retryAfterMillis = oldest + windowMillis - now;
            return RateLimitResult.deny((retryAfterMillis + 999) / 1000);
        }
    }

    private void evictExpired(ArrayDeque<Long> window, long now) {
        while (!window.isEmpty() && window.peekFirst() <= now - windowMillis) {
            window.pollFirst();
        }
    }
}

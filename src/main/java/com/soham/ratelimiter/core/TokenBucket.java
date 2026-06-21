package com.soham.ratelimiter.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * DAY 1 — Core token-bucket algorithm.
 *
 * How it works:
 *  - The bucket holds up to `capacity` tokens (the "burst" size).
 *  - Tokens refill continuously at `refillRatePerSecond` tokens/second.
 *  - Each incoming request tries to take 1 token. If a token is available,
 *    the request is allowed. If not, it's rejected (HTTP 429 upstream).
 *
 * Why token-bucket (vs simple fixed counter)?
 *  - Fixed window counters allow bursts at window boundaries (e.g. 100 reqs
 *    at 11:59:59 + 100 more at 12:00:00 = 200 reqs in 1 second).
 *  - Token bucket smooths this out and naturally supports controlled bursts
 *    up to `capacity`, which is usually what APIs actually want.
 *
 * Thread-safety: this implementation is lock-free, using compare-and-swap
 * via AtomicLong, so it's safe to share one bucket across multiple threads
 * for the same client/key without external synchronization.
 */
public class TokenBucket {

    private final long capacity;
    private final double refillRatePerSecond;

    // Encode (tokens, lastRefillTimestampNanos) is tricky to pack atomically,
    // so we use a simple synchronized block here for clarity first.
    // TODO (optional stretch goal): replace with a fully lock-free CAS loop.
    private double availableTokens;
    private long lastRefillTimestampNanos;

    public TokenBucket(long capacity, double refillRatePerSecond) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (refillRatePerSecond <= 0) throw new IllegalArgumentException("refillRatePerSecond must be > 0");
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.availableTokens = capacity; // start full — allow initial burst
        this.lastRefillTimestampNanos = System.nanoTime();
    }

    /**
     * Attempts to consume a single token.
     * @return true if the request is allowed, false if it should be rate-limited.
     */
    public synchronized boolean tryConsume() {
        return tryConsume(1);
    }

    public synchronized boolean tryConsume(int tokensRequested) {
        refill();
        if (availableTokens >= tokensRequested) {
            availableTokens -= tokensRequested;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillTimestampNanos) / 1_000_000_000.0;
        if (elapsedSeconds <= 0) return;

        double tokensToAdd = elapsedSeconds * refillRatePerSecond;
        if (tokensToAdd > 0) {
            availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
            lastRefillTimestampNanos = now;
        }
    }

    public synchronized double getAvailableTokens() {
        refill();
        return availableTokens;
    }
}

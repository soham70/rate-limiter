package com.soham.ratelimiter.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    @Test
    void allowsRequestsUpToCapacity() {
        // capacity 5, refill rate irrelevant for this burst test
        TokenBucket bucket = new TokenBucket(5, 1);

        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(), "request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void rejectsRequestsBeyondCapacity() {
        TokenBucket bucket = new TokenBucket(3, 1);

        for (int i = 0; i < 3; i++) {
            assertTrue(bucket.tryConsume());
        }
        // bucket should now be empty
        assertFalse(bucket.tryConsume(), "4th request should be rejected, bucket is empty");
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        // capacity 2, refill 10 tokens/sec -> 1 token refills every 100ms
        TokenBucket bucket = new TokenBucket(2, 10);

        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume(), "bucket should be empty immediately after burst");

        Thread.sleep(150); // allow ~1.5 tokens to refill

        assertTrue(bucket.tryConsume(), "token should have refilled after waiting");
    }

    @Test
    void neverExceedsCapacityEvenAfterLongIdle() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(3, 100); // fast refill

        Thread.sleep(200); // would refill way more than capacity if uncapped

        assertEquals(3, bucket.getAvailableTokens(), 0.01,
                "available tokens should be capped at capacity, not overflow");
    }

    @Test
    void rejectsInvalidConstructorArgs() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-1, 1));
    }
}

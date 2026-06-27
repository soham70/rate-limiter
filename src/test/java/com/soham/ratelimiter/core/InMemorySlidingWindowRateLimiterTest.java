package com.soham.ratelimiter.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemorySlidingWindowRateLimiterTest {

    @Test
    void allowsRequestsUpToMaxInWindow() {
        InMemorySlidingWindowRateLimiter limiter = new InMemorySlidingWindowRateLimiter(3, 10);

        assertTrue(limiter.tryConsume("client-a").permitted());
        assertTrue(limiter.tryConsume("client-a").permitted());
        assertTrue(limiter.tryConsume("client-a").permitted());
    }

    @Test
    void rejectsRequestsBeyondMaxInWindow() {
        InMemorySlidingWindowRateLimiter limiter = new InMemorySlidingWindowRateLimiter(3, 10);

        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryConsume("client-a").permitted());
        }
        assertFalse(limiter.tryConsume("client-a").permitted());
    }

    @Test
    void isolatesKeys() {
        InMemorySlidingWindowRateLimiter limiter = new InMemorySlidingWindowRateLimiter(1, 10);

        assertTrue(limiter.tryConsume("client-a").permitted());
        assertTrue(limiter.tryConsume("client-b").permitted());
        assertFalse(limiter.tryConsume("client-a").permitted());
    }

    @Test
    void allowsRequestsAfterWindowSlides() throws InterruptedException {
        InMemorySlidingWindowRateLimiter limiter = new InMemorySlidingWindowRateLimiter(1, 1);

        assertTrue(limiter.tryConsume("client-a").permitted());
        assertFalse(limiter.tryConsume("client-a").permitted());

        Thread.sleep(1100);

        assertTrue(limiter.tryConsume("client-a").permitted());
    }

    @Test
    void rejectsInvalidConstructorArgs() {
        assertThrows(IllegalArgumentException.class, () -> new InMemorySlidingWindowRateLimiter(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new InMemorySlidingWindowRateLimiter(5, 0));
    }
}

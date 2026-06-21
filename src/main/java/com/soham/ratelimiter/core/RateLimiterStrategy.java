package com.soham.ratelimiter.core;

/**
 * Common interface for rate-limiting strategies.
 *
 * DAY 4 TODO: implement a SlidingWindowRateLimiter that also satisfies this
 * interface, then make the strategy pluggable via config (e.g.
 * `ratelimiter.strategy=token-bucket` vs `ratelimiter.strategy=sliding-window`).
 * That's what turns this from "a rate limiter" into "a rate limiter library
 * with interchangeable strategies" — much stronger portfolio story.
 */
public interface RateLimiterStrategy {
    boolean tryConsume(String key);
}

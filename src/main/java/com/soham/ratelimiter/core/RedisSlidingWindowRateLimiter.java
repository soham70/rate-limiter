package com.soham.ratelimiter.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;

/**
 * Distributed sliding-window limiter using a Redis sorted set (ZSET).
 *
 * Each allowed request adds a member scored by timestamp. Expired entries
 * are removed atomically inside a Lua script before counting.
 */
public class RedisSlidingWindowRateLimiter implements RateLimiterStrategy {

    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local window_ms = tonumber(ARGV[1])
            local max_requests = tonumber(ARGV[2])
            local now_ms = tonumber(ARGV[3])
            local member = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', key, 0, now_ms - window_ms)
            local count = redis.call('ZCARD', key)

            if count < max_requests then
              redis.call('ZADD', key, now_ms, member)
              redis.call('EXPIRE', key, math.ceil(window_ms / 1000) + 1)
              return 1
            end

            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            if oldest[2] == nil then
              return 0
            end

            local retry_after_ms = tonumber(oldest[2]) + window_ms - now_ms
            if retry_after_ms < 0 then
              retry_after_ms = 0
            end
            return -retry_after_ms
            """;

    private final StringRedisTemplate redis;
    private final long maxRequests;
    private final long windowMillis;
    private final DefaultRedisScript<Long> script;

    public RedisSlidingWindowRateLimiter(StringRedisTemplate redis, long maxRequests, long windowSeconds) {
        this.redis = redis;
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
        this.script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
    }

    @Override
    public RateLimitResult tryConsume(String key) {
        Long result = redis.execute(
                script,
                List.of("ratelimit:sw:" + key),
                String.valueOf(windowMillis),
                String.valueOf(maxRequests),
                String.valueOf(System.currentTimeMillis()),
                UUID.randomUUID().toString()
        );

        if (result != null && result == 1L) {
            return RateLimitResult.permit();
        }
        if (result != null && result < 0L) {
            return RateLimitResult.deny((-result + 999) / 1000);
        }
        return RateLimitResult.deny(windowMillis / 1000);
    }
}

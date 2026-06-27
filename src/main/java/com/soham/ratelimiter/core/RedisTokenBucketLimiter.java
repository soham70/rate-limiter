package com.soham.ratelimiter.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;

/**
 * Distributed token-bucket limiter using Redis + a Lua script for atomicity.
 *
 * The Lua script performs refill, check, and decrement in a single Redis
 * round-trip so concurrent requests from multiple app instances cannot
 * over-consume tokens due to race conditions.
 */
public class RedisTokenBucketLimiter implements RateLimiterStrategy {

    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now_ms = tonumber(ARGV[3])

            local tokens = tonumber(redis.call('HGET', key, 'tokens'))
            local last_refill = tonumber(redis.call('HGET', key, 'last_refill'))

            if tokens == nil then
              tokens = capacity
              last_refill = now_ms
            end

            local elapsed_sec = (now_ms - last_refill) / 1000.0
            if elapsed_sec > 0 then
              tokens = math.min(capacity, tokens + (elapsed_sec * refill_rate))
              last_refill = now_ms
            end

            local ttl = math.ceil(capacity / refill_rate) + 60

            if tokens >= 1 then
              tokens = tokens - 1
              redis.call('HSET', key, 'tokens', tokens, 'last_refill', last_refill)
              redis.call('EXPIRE', key, ttl)
              return 1
            end

            redis.call('HSET', key, 'tokens', tokens, 'last_refill', last_refill)
            redis.call('EXPIRE', key, ttl)
            return 0
            """;

    private final StringRedisTemplate redis;
    private final long capacity;
    private final double refillRatePerSecond;
    private final DefaultRedisScript<Long> script;

    public RedisTokenBucketLimiter(StringRedisTemplate redis, long capacity, double refillRatePerSecond) {
        this.redis = redis;
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.script = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);
    }

    @Override
    public RateLimitResult tryConsume(String key) {
        Long allowed = redis.execute(
                script,
                List.of("ratelimit:tb:" + key),
                String.valueOf(capacity),
                String.valueOf(refillRatePerSecond),
                String.valueOf(System.currentTimeMillis())
        );

        if (allowed != null && allowed == 1L) {
            return RateLimitResult.permit();
        }
        long retryAfter = (long) Math.ceil(1.0 / refillRatePerSecond);
        return RateLimitResult.deny(retryAfter);
    }
}

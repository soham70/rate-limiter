package com.soham.ratelimiter.config;

import com.soham.ratelimiter.core.InMemorySlidingWindowRateLimiter;
import com.soham.ratelimiter.core.InMemoryTokenBucketLimiter;
import com.soham.ratelimiter.core.RateLimiterStrategy;
import com.soham.ratelimiter.core.RedisSlidingWindowRateLimiter;
import com.soham.ratelimiter.core.RedisTokenBucketLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterConfiguration {

    @Bean
    @ConditionalOnExpression("'${ratelimiter.backend:in-memory}' == 'in-memory' && '${ratelimiter.strategy:token-bucket}' == 'token-bucket'")
    public RateLimiterStrategy inMemoryTokenBucket(RateLimiterProperties properties) {
        return new InMemoryTokenBucketLimiter(properties.getCapacity(), properties.getRefillRate());
    }

    @Bean
    @ConditionalOnExpression("'${ratelimiter.backend:in-memory}' == 'in-memory' && '${ratelimiter.strategy:token-bucket}' == 'sliding-window'")
    public RateLimiterStrategy inMemorySlidingWindow(RateLimiterProperties properties) {
        return new InMemorySlidingWindowRateLimiter(
                properties.getMaxRequests(), properties.getWindowSeconds());
    }

    @Bean
    @ConditionalOnExpression("'${ratelimiter.backend:in-memory}' == 'redis' && '${ratelimiter.strategy:token-bucket}' == 'token-bucket'")
    public RateLimiterStrategy redisTokenBucket(RateLimiterProperties properties, StringRedisTemplate redis) {
        return new RedisTokenBucketLimiter(redis, properties.getCapacity(), properties.getRefillRate());
    }

    @Bean
    @ConditionalOnExpression("'${ratelimiter.backend:in-memory}' == 'redis' && '${ratelimiter.strategy:token-bucket}' == 'sliding-window'")
    public RateLimiterStrategy redisSlidingWindow(RateLimiterProperties properties, StringRedisTemplate redis) {
        return new RedisSlidingWindowRateLimiter(
                redis, properties.getMaxRequests(), properties.getWindowSeconds());
    }
}

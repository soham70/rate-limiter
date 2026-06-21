package com.soham.ratelimiter.config;

import com.soham.ratelimiter.core.InMemoryTokenBucketLimiter;
import com.soham.ratelimiter.core.RateLimiterStrategy;
import com.soham.ratelimiter.filter.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Tunable via application.yml -> ratelimiter.capacity / ratelimiter.refill-rate
    @Value("${ratelimiter.capacity:5}")
    private long capacity;

    @Value("${ratelimiter.refill-rate:1.0}")
    private double refillRatePerSecond;

    @Bean
    public RateLimiterStrategy rateLimiterStrategy() {
        // DAY 3 TODO: swap this bean for a RedisTokenBucketLimiter
        // (same RateLimiterStrategy interface) once you add Redis —
        // nothing else in this config needs to change. That's the payoff
        // of coding to an interface from day one.
        return new InMemoryTokenBucketLimiter(capacity, refillRatePerSecond);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimiterStrategy()))
                .addPathPatterns("/api/**");
    }
}

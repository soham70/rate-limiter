package com.soham.ratelimiter.config;

import com.soham.ratelimiter.core.RateLimiterStrategy;
import com.soham.ratelimiter.filter.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiterStrategy rateLimiterStrategy;

    public WebConfig(RateLimiterStrategy rateLimiterStrategy) {
        this.rateLimiterStrategy = rateLimiterStrategy;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimiterStrategy))
                .addPathPatterns("/api/**");
    }
}

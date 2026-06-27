package com.soham.ratelimiter.filter;

import com.soham.ratelimiter.core.RateLimiterStrategy;
import com.soham.ratelimiter.core.RateLimitResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Wires the rate limiter into the HTTP request lifecycle.
 *
 * Keying: rate-limits by X-API-Key header if present, otherwise client IP.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final RateLimiterStrategy rateLimiter;

    public RateLimitInterceptor(RateLimiterStrategy rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = resolveKey(request);
        RateLimitResult result = rateLimiter.tryConsume(key);

        if (result.permitted()) {
            return true;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        try {
            response.getWriter().write(
                    "{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests. Please slow down.\"," +
                            "\"retryAfterSeconds\":" + result.retryAfterSeconds() + "}"
            );
        } catch (Exception ignored) {
            // response stream already committed/closed
        }
        return false;
    }

    private String resolveKey(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }
        return "ip:" + request.getRemoteAddr();
    }
}

package com.soham.ratelimiter.filter;

import com.soham.ratelimiter.core.RateLimiterStrategy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * DAY 2 — Wires the core algorithm into the HTTP request lifecycle.
 *
 * Keying strategy: rate-limits by API key header if present, otherwise
 * falls back to client IP. This mirrors how real APIs behave (authenticated
 * clients get their own bucket; anonymous/public traffic is limited by IP).
 *
 * TODO (Day 2 stretch): add a `Retry-After` header to 429 responses,
 * computed from the bucket's refill rate, so clients know exactly how
 * long to back off.
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

        if (rateLimiter.tryConsume(key)) {
            return true;
        }

        response.setStatus(429); // HTTP 429 Too Many Requests
        response.setContentType("application/json");
        try {
            response.getWriter().write(
                    "{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests. Please slow down.\"}"
            );
        } catch (Exception ignored) {
            // response stream already committed/closed — nothing more we can do
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

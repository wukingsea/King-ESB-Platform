package com.king.esb.core.route;

/**
 * Fixed-window rate limit configuration for a route.
 *
 * @param requestsPerWindow allowed requests within the window
 * @param windowSeconds     length of the window in seconds
 * @param keyBy             how to partition the counter: {@code ip}, {@code apikey} or a header name
 */
public record RateLimitConfig(int requestsPerWindow, int windowSeconds, String keyBy) {

    public RateLimitConfig {
        keyBy = (keyBy == null || keyBy.isBlank()) ? "ip" : keyBy;
    }

    public boolean isActive() {
        return requestsPerWindow > 0 && windowSeconds > 0;
    }
}

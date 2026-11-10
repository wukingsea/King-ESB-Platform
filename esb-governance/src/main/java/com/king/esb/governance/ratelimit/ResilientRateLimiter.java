package com.king.esb.governance.ratelimit;

import com.king.esb.core.spi.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator that falls back to an in-memory limiter when the primary (typically Redis) throws.
 * This "fail open to a local window" behaviour keeps the gateway responsive during a Redis outage.
 */
public class ResilientRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ResilientRateLimiter.class);

    private final RateLimiter primary;
    private final RateLimiter fallback;

    public ResilientRateLimiter(RateLimiter primary, RateLimiter fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        try {
            return primary.tryAcquire(key, limit, windowSeconds);
        } catch (RuntimeException e) {
            log.warn("Primary rate limiter failed for key {} ({}); using in-memory fallback",
                    key, e.getMessage());
            return fallback.tryAcquire(key, limit, windowSeconds);
        }
    }
}

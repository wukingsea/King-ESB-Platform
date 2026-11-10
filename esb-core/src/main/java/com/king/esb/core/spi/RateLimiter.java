package com.king.esb.core.spi;

/**
 * Fixed-window rate limiter SPI. Implementations may be Redis-backed (distributed) or in-process.
 * {@link #tryAcquire} must never throw for infrastructure reasons; a resilient implementation
 * fails open so that a limiter outage cannot take down the gateway.
 */
public interface RateLimiter {

    boolean tryAcquire(String key, int limit, int windowSeconds);
}

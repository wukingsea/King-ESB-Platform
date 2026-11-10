package com.king.esb.governance.ratelimit;

import com.king.esb.core.spi.RateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis-backed fixed-window rate limiter using {@code INCR} + {@code EXPIRE}. Shares counters
 * across nodes; infrastructure errors propagate so the {@link ResilientRateLimiter} can fail over.
 */
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        if (limit <= 0) {
            return true;
        }
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            return true;
        }
        if (count == 1L) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return count <= limit;
    }
}

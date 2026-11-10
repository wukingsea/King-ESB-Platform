package com.king.esb.governance.ratelimit;

import com.king.esb.core.spi.RateLimiter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process sliding-window rate limiter. Used as the fallback when Redis is unavailable and for
 * single-node deployments. A bounded sweep keeps the key map from growing without bound.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private static final long MAX_WINDOWS = 100_000L;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        if (windows.size() > MAX_WINDOWS) {
            windows.clear();
        }
        Deque<Long> timestamps = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}

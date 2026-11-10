package com.king.esb.governance.config;

import com.king.esb.core.spi.RateLimiter;
import com.king.esb.governance.ratelimit.InMemoryRateLimiter;
import com.king.esb.governance.ratelimit.RedisRateLimiter;
import com.king.esb.governance.ratelimit.ResilientRateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the rate limiter: Redis-backed when a {@link StringRedisTemplate} is available, always
 * wrapped in a resilient decorator that falls back to an in-memory window on Redis failure.
 */
@Configuration
public class EsbGovernanceConfiguration {

    @Bean
    public RateLimiter rateLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        InMemoryRateLimiter fallback = new InMemoryRateLimiter();
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return fallback;
        }
        return new ResilientRateLimiter(new RedisRateLimiter(redisTemplate), fallback);
    }
}

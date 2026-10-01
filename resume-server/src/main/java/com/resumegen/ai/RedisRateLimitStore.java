package com.resumegen.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 限流（resume.cache.type=redis）：固定窗口 INCR + EXPIRE，多实例全局一致。
 *
 * <p>key 为 {@code ai:rl:{userId}:{taskType}}；首次计数为 1 时设置窗口过期，
 * 过期后自动从零开始。采用「INCR 后条件 EXPIRE」近似，极端并发下窗口边界可能略有偏差。
 */
@Component
@ConditionalOnProperty(name = "resume.cache.type", havingValue = "redis")
public class RedisRateLimitStore implements RateLimitStore {

    private static final String KEY_PREFIX = "ai:rl:";

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        String k = KEY_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(k);
        if (count != null && count == 1L) {
            redisTemplate.expire(k, Duration.ofSeconds(windowSeconds));
        }
        return count != null && count <= limit;
    }
}
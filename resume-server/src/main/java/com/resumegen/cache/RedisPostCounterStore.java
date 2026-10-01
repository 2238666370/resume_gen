package com.resumegen.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 计数写扩散实现：resume.cache.type=redis。
 *
 * <p>Hash key 为 {@code post:counter:{postId}}，字段为 like/collect/comment/report。
 * 计数以 DB 写透为持久化兜底，此处仅作高频读模型；命中空时由调用方读穿透回填自愈。
 */
@Component
@ConditionalOnProperty(name = "resume.cache.type", havingValue = "redis")
public class RedisPostCounterStore implements PostCounterStore {

    /** 计数缓存 TTL（秒）：过期后由读穿透从 DB 重建，天然收敛漂移、保证最终一致。 */
    private static final long TTL_SECONDS = 24 * 60 * 60L;

    private static final String KEY_PREFIX = "post:counter:";

    private final StringRedisTemplate redisTemplate;

    public RedisPostCounterStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incr(Long postId, Field field, long delta) {
        String key = key(postId);
        redisTemplate.opsForHash().increment(key, field.key(), delta);
        // 保证 Key 有 TTL，过期后自愈
        redisTemplate.expire(key, java.time.Duration.ofSeconds(TTL_SECONDS));
    }

    @Override
    public Map<Field, Long> getAll(Long postId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(postId));
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        Map<Field, Long> result = new EnumMap<>(Field.class);
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            Field field = fieldOf(String.valueOf(e.getKey()));
            if (field != null && e.getValue() != null) {
                result.put(field, parseLong(e.getValue()));
            }
        }
        return result.isEmpty() ? null : result;
    }

    @Override
    public Map<Long, Map<Field, Long>> getAll(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<Field, Long>> result = new java.util.HashMap<>();
        for (Long postId : postIds) {
            Map<Field, Long> c = getAll(postId);
            if (c != null) {
                result.put(postId, c);
            }
        }
        return result;
    }

    @Override
    public void saveAll(Long postId, Map<Field, Long> counters) {
        if (counters == null || counters.isEmpty()) {
            return;
        }
        Map<String, String> values = new java.util.HashMap<>();
        for (Map.Entry<Field, Long> e : counters.entrySet()) {
            values.put(e.getKey().key(), String.valueOf(e.getValue()));
        }
        String key = key(postId);
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, java.time.Duration.ofSeconds(TTL_SECONDS));
    }

    @Override
    public void delete(Long postId) {
        redisTemplate.delete(key(postId));
    }

    private String key(Long postId) {
        return KEY_PREFIX + postId;
    }

    private Field fieldOf(String name) {
        for (Field f : Field.values()) {
            if (f.key().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private long parseLong(Object v) {
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
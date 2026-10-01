package com.resumegen.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 无缓存实现：resume.cache.type=none（默认）。
 */
@Component
@ConditionalOnProperty(name = "resume.cache.type", havingValue = "none", matchIfMissing = true)
public class NoopCacheService implements CacheService {

    @Override
    public void set(String key, String value, long ttlSeconds) {
        // no-op
    }

    @Override
    public String get(String key) {
        return null;
    }

    @Override
    public void delete(String key) {
        // no-op
    }
}
package com.resumegen.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 无缓存计数实现：resume.cache.type=none（默认）。
 * 计数读写完全由 CommunityService 直接操作 community_post 计数列，这里为空操作。
 */
@Component
@ConditionalOnProperty(name = "resume.cache.type", havingValue = "none", matchIfMissing = true)
public class NoopPostCounterStore implements PostCounterStore {

    @Override
    public void incr(Long postId, Field field, long delta) {
        // no-op：DB 计数列由调用方写透
    }

    @Override
    public Map<Field, Long> getAll(Long postId) {
        return null;
    }

    @Override
    public Map<Long, Map<Field, Long>> getAll(List<Long> postIds) {
        return Collections.emptyMap();
    }

    @Override
    public void saveAll(Long postId, Map<Field, Long> counters) {
        // no-op
    }

    @Override
    public void delete(Long postId) {
        // no-op
    }
}
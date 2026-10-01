package com.resumegen.cache;

/**
 * 缓存抽象：redis / none（none 为无缓存直连，保证无 Redis 也能运行）。
 * 统一操作字符串（如 JSON 序列化结果）。
 */
public interface CacheService {

    void set(String key, String value, long ttlSeconds);

    String get(String key);

    void delete(String key);
}
package com.resumegen.ai;

/**
 * 限流计数存储抽象：按 key 在窗口内计数，返回是否放行。
 *
 * <p>redis 实现用 INCR + EXPIRE 原子计数（多实例全局一致）；none 实现用本地
 * 滑动窗口近似（单机精确，多实例不严格）。
 */
public interface RateLimitStore {

    /**
     * 尝试消耗 1 个配额。
     *
     * @param key           计数维度键（如 {@code userId:taskType}）
     * @param limit         窗口内上限
     * @param windowSeconds 窗口时长（秒）
     * @return true=放行，false=已达上限
     */
    boolean tryAcquire(String key, int limit, int windowSeconds);
}
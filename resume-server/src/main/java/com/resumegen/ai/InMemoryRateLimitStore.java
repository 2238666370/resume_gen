package com.resumegen.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 本地内存限流（resume.cache.type=none，默认）：按 key 维护滑动窗口时间戳，
 * 单机精确，多实例不严格。带定时清理线程避免 key 无限增长。
 */
@Component
@ConditionalOnProperty(name = "resume.cache.type", havingValue = "none", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ai-rate-limit-cleaner");
        t.setDaemon(true);
        return t;
    });

    public InMemoryRateLimitStore() {
        cleaner.scheduleAtFixedRate(this::purge, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long threshold = now - windowSeconds * 1000L;
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            while (!w.stamps.isEmpty() && w.stamps.peekFirst() < threshold) {
                w.stamps.pollFirst();
            }
            if (w.stamps.size() >= limit) {
                return false;
            }
            w.stamps.addLast(now);
            return true;
        }
    }

    private void purge() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Window> e = it.next();
            Window w = e.getValue();
            synchronized (w) {
                while (!w.stamps.isEmpty() && w.stamps.peekFirst() < now - 120_000L) {
                    w.stamps.pollFirst();
                }
                if (w.stamps.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        cleaner.shutdownNow();
    }

    /** 某个 key 的窗口（最近一次请求时间戳序列）。 */
    private static class Window {
        final Deque<Long> stamps = new ArrayDeque<>();
    }
}
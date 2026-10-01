package com.resumegen.mq;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 进程内消息队列实现（无 Redis 降级 / 单测兜底）。
 *
 * <p>每个队列一个 {@link LinkedBlockingQueue}，订阅后按 {@code concurrency}
 * 个消费者并发取消费；不同队列（如 high/low）通过不同的并发度实现资源差异化分配。
 * 仅适用于单实例、可容忍丢失的场景；生产默认走 {@link RedisStreamMessageQueue}。
 */
@Component
@ConditionalOnProperty(name = "mq.type", havingValue = "memory")
public class InMemoryMessageQueue implements MessageQueue {

    private final Map<String, BlockingQueue<String>> queues = new ConcurrentHashMap<>();
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
    private final List<ExecutorService> executors = new CopyOnWriteArrayList<>();

    @Override
    public void publish(String queue, String payload) {
        queueOf(queue).add(payload);
    }

    @Override
    public synchronized void subscribe(String queue, int concurrency, Handler handler) {
        if (!subscriptions.add(queue)) {
            return;
        }
        BlockingQueue<String> q = queueOf(queue);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, concurrency));
        executors.add(pool);
        for (int i = 0; i < Math.max(1, concurrency); i++) {
            pool.submit(() -> consume(q, handler));
        }
    }

    private void consume(BlockingQueue<String> q, Handler handler) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String payload = q.take();
                try {
                    handler.handle(payload);
                } catch (Exception ignored) {
                    // 单条消息处理失败不终止消费者
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private BlockingQueue<String> queueOf(String name) {
        return queues.computeIfAbsent(name, k -> new LinkedBlockingQueue<>());
    }

    @PreDestroy
    void shutdown() {
        executors.forEach(ExecutorService::shutdownNow);
    }
}
package com.resumegen.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存任务登记表：mq.type=memory 时生效（无 Redis 降级 / 单测）。
 */
@Component
@ConditionalOnProperty(name = "mq.type", havingValue = "memory")
public class InMemoryAiTaskStore implements AiTaskStore {

    private final Map<String, AiTaskRecord> tasks = new ConcurrentHashMap<>();

    @Override
    public void put(AiTaskRecord task) {
        tasks.put(task.getTaskId(), task);
    }

    @Override
    public AiTaskRecord get(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public void remove(String taskId) {
        tasks.remove(taskId);
    }
}

package com.resumegen.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 任务登记表：mq.type=stream 时生效，任务状态持久化到 Redis Hash
 * {@code ai:task:{taskId}}（TTL 1h），进程重启后轮询/SSE 仍可查询（O3）。
 */
@Component
@ConditionalOnProperty(name = "mq.type", havingValue = "stream", matchIfMissing = true)
public class RedisAiTaskStore implements AiTaskStore {

    private static final String PREFIX = "ai:task:";
    private static final long TTL_SECONDS = 3600;

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public RedisAiTaskStore(StringRedisTemplate redis, ObjectMapper om) {
        this.redis = redis;
        this.om = om;
    }

    @Override
    public void put(AiTaskRecord task) {
        try {
            redis.opsForValue().set(PREFIX + task.getTaskId(), om.writeValueAsString(task),
                    Duration.ofSeconds(TTL_SECONDS));
        } catch (Exception ignored) {
            // 持久化失败不影响主流程（结果已回内存，仅失去重启可查能力）
        }
    }

    @Override
    public AiTaskRecord get(String taskId) {
        String json = redis.opsForValue().get(PREFIX + taskId);
        if (json == null) {
            return null;
        }
        try {
            return om.readValue(json, AiTaskRecord.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void remove(String taskId) {
        redis.delete(PREFIX + taskId);
    }
}

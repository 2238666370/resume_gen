package com.resumegen.mq;

import com.resumegen.config.MqProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Redis Stream 消息队列实现（R10-O1，默认）。
 *
 * <p>每个队列对应一个 Stream（{@code mq:stream:{queue}}）与消费者组
 * （{@code mq:group:{queue}}），消费采用「消费者组 + ACK」at-least-once 语义。
 *
 * <p><b>可靠投递</b>：{@code publish} 同步 XADD 并校验返回 ID，投递失败抛异常交由调用方
 * 兜底（AI 任务回滚登记表 / 埋点降级同步直写）；消息持久化于 Redis AOF/RDB，进程重启不丢。
 *
 * <p><b>消费兜底</b>：处理成功 XACK；失败不 ACK 滞留 PEL，由重试线程定期
 * {@code XPENDING + XCLAIM}（{@code claim-idle-ms} 判卡死）接管重投；单条消息重试次数
 * 超过 {@code retry.max} 后 XACK 并转死信 Stream（{@code mq:stream:{queue}:dlq}，含原始
 * payload 与 error，可观测、可人工补偿）。
 *
 * <p><b>幂等</b>：Stream 为 at-least-once（消费者崩溃后 XCLAIM 会重复投递），因此各
 * 消费者处理必须幂等——如 AI 任务按 taskId 判终态跳过（见 {@code AiAsyncService}）、
 * 埋点分钟级 upsert 幂等。
 */
@Component
@ConditionalOnProperty(name = "mq.type", havingValue = "stream", matchIfMissing = true)
public class RedisStreamMessageQueue implements MessageQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamMessageQueue.class);
    private static final String FIELD_PAYLOAD = "payload";
    private static final String FIELD_ERROR = "error";

    private final StringRedisTemplate redis;
    private final MqProperties props;
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    public RedisStreamMessageQueue(StringRedisTemplate redis, MqProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public void publish(String queue, String payload) {
        RecordId id = redis.opsForStream().add(record(streamKey(queue), payload, null));
        if (id == null) {
            // 可靠投递：XADD 返回 null 视为投递失败，抛异常由调用方兜底（如降级同步/回滚登记）
            throw new IllegalStateException("Redis Stream 投递失败 queue=" + queue);
        }
    }

    @Override
    public synchronized void subscribe(String queue, int concurrency, Handler handler) {
        if (!subscriptions.add(queue)) {
            return;
        }
        String streamKey = streamKey(queue);
        String group = group(queue);
        ensureGroup(streamKey, group);
        int n = Math.max(1, concurrency);
        for (int i = 0; i < n; i++) {
            executor.submit(() -> consume(streamKey, group, handler));
        }
        executor.submit(() -> retryLoop(streamKey, group, handler));
    }

    // ---------- 消费 ----------

    private void consume(String streamKey, String group, Handler handler) {
        String consumer = props.getRedis().getConsumerPrefix() + UUID.randomUUID();
        StreamOperations<String, Object, Object> ops = redis.opsForStream();
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = ops.read(
                        Consumer.from(group, consumer),
                        StreamReadOptions.empty().count(10)
                                .block(Duration.ofMillis(props.getRedis().getBlockMs())),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
                for (MapRecord<String, Object, Object> rec : records) {
                    handleWithRetry(streamKey, group, rec, handler);
                }
            } catch (Exception e) {
                if (running) {
                    log.warn("Redis Stream 消费异常 stream={}, 稍后重试", streamKey, e);
                    sleep(1000);
                }
            }
        }
    }

    private void retryLoop(String streamKey, String group, Handler handler) {
        StreamOperations<String, Object, Object> ops = redis.opsForStream();
        while (running) {
            sleep(props.getRedis().getRetryIntervalMs());
            if (!running) {
                break;
            }
            try {
                PendingMessages pending = ops.pending(streamKey, group, Range.unbounded(), 100);
                String retryConsumer = props.getRedis().getConsumerPrefix() + "retry-" + UUID.randomUUID();
                for (PendingMessage pm : pending) {
                    Duration elapsed = pm.getElapsedTimeSinceLastDelivery();
                    long idleMs = elapsed == null ? 0 : elapsed.toMillis();
                    if (idleMs < props.getRedis().getClaimIdleMs()) {
                        continue;
                    }
                    List<MapRecord<String, Object, Object>> claimed = ops.claim(
                            streamKey, group, retryConsumer,
                            Duration.ofMillis(props.getRedis().getClaimIdleMs()), pm.getId());
                    for (MapRecord<String, Object, Object> rec : claimed) {
                        handleWithRetry(streamKey, group, rec, handler);
                    }
                }
            } catch (Exception e) {
                log.warn("Redis Stream 重试扫描异常 stream={}", streamKey, e);
            }
        }
    }

    private void handleWithRetry(String streamKey, String group,
                                 MapRecord<String, Object, Object> rec, Handler handler) {
        String id = rec.getId().getValue();
        String payload = String.valueOf(rec.getValue().get(FIELD_PAYLOAD));
        try {
            handler.handle(payload);
            redis.opsForStream().acknowledge(streamKey, group, id);
            clearRetry(streamKey, id);
        } catch (Exception e) {
            long retries = incrRetry(streamKey, id);
            if (retries > props.getRedis().getRetryMax()) {
                redis.opsForStream().acknowledge(streamKey, group, id);
                if (props.getRedis().isDeadLetter()) {
                    redis.opsForStream().add(record(streamKey + ":dlq", payload, String.valueOf(e)));
                }
                clearRetry(streamKey, id);
                log.warn("消息重试超限转入死信 stream={} id={}", streamKey, id);
            }
            // 未超限：不 ACK，滞留 PEL 待重试线程接管
        }
    }

    // ---------- 内部 ----------

    private void ensureGroup(String streamKey, String group) {
        try {
            redis.opsForStream().createGroup(streamKey, group);
        } catch (Exception e) {
            // 组已存在（BUSYGROUP）或连接暂不可用均不阻断启动，消费线程会自行重连重试
            log.debug("创建消费者组跳过 stream={} group={} cause={}", streamKey, group, e.getMessage());
        }
    }

    private MapRecord<String, Object, Object> record(String key, String payload, String error) {
        Map<Object, Object> body = new java.util.HashMap<>();
        body.put(FIELD_PAYLOAD, payload == null ? "" : payload);
        if (error != null) {
            body.put(FIELD_ERROR, error);
        }
        return StreamRecords.mapBacked(body).withStreamKey(key);
    }

    private String streamKey(String queue) {
        return props.getRedis().getStreamPrefix() + queue;
    }

    private String group(String queue) {
        return props.getRedis().getGroupPrefix() + queue;
    }

    private String retryKey(String streamKey) {
        return "mq:retry:" + streamKey;
    }

    private long incrRetry(String streamKey, String id) {
        Long v = redis.opsForHash().increment(retryKey(streamKey), id, 1);
        return v == null ? 1 : v;
    }

    private void clearRetry(String streamKey, String id) {
        redis.opsForHash().delete(retryKey(streamKey), id);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void shutdown() {
        running = false;
        executor.shutdownNow();
    }
}

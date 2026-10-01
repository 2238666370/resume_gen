package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.config.StatsProperties;
import com.resumegen.entity.AccessLog;
import com.resumegen.mq.MessageQueue;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 埋点异步消费者（R10-O2）：订阅独立队列 {@value #QUEUE}，异步落库，避免高并发直写阻塞主链路。
 */
@Component
public class StatLogConsumer {

    public static final String QUEUE = "stat";
    private static final int CONCURRENCY = 3;

    private final MessageQueue queue;
    private final StatLogWriter statLogWriter;
    private final ObjectMapper om;
    private final StatsProperties props;

    public StatLogConsumer(MessageQueue queue, StatLogWriter statLogWriter,
                           ObjectMapper om, StatsProperties props) {
        this.queue = queue;
        this.statLogWriter = statLogWriter;
        this.om = om;
        this.props = props;
    }

    @PostConstruct
    void start() {
        if (!props.getAsync().isEnabled()) {
            return;
        }
        queue.subscribe(QUEUE, CONCURRENCY, this::handle);
    }

    void handle(String payload) {
        try {
            AccessLog log = om.readValue(payload, AccessLog.class);
            statLogWriter.write(log);
        } catch (Exception ignored) {
            // 单条解析/落库失败丢弃（埋点可容忍丢失，不重试避免阻塞）
        }
    }
}

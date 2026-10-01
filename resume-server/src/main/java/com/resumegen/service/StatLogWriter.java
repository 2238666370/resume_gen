package com.resumegen.service;

import com.resumegen.entity.AccessLog;
import com.resumegen.mapper.AccessLogMapper;
import com.resumegen.mapper.StatMinuteMapper;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

/**
 * 埋点落库写入器（R10-O2）：同步 / 异步消费者共用，避免重复逻辑。
 */
@Component
public class StatLogWriter {

    private final AccessLogMapper accessLogMapper;
    private final StatMinuteMapper statMinuteMapper;

    public StatLogWriter(AccessLogMapper accessLogMapper, StatMinuteMapper statMinuteMapper) {
        this.accessLogMapper = accessLogMapper;
        this.statMinuteMapper = statMinuteMapper;
    }

    /** 落库一条访问事件：写 access_log + 分钟级 PV 幂等 upsert。 */
    public void write(AccessLog log) {
        accessLogMapper.insert(log);
        statMinuteMapper.upsertPv(log.getTs().truncatedTo(ChronoUnit.MINUTES));
    }
}

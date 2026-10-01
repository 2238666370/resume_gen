package com.resumegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息队列配置（R10-O1）：默认 Redis Stream，可降级 memory。
 */
@Data
@ConfigurationProperties(prefix = "mq")
public class MqProperties {

    /** 队列实现：stream（Redis Stream） | kafka | rabbitmq | memory（进程内降级/单测）。 */
    private String type = "stream";

    private Redis redis = new Redis();

    @Data
    public static class Redis {
        /** Stream key 前缀。 */
        private String streamPrefix = "mq:stream:";
        /** 消费者组前缀。 */
        private String groupPrefix = "mq:group:";
        /** 消费者名前缀（多实例唯一，追加 UUID）。 */
        private String consumerPrefix = "mq:consumer:";
        /** XREADGROUP 阻塞时长（毫秒）。 */
        private long blockMs = 2000;
        /** 单条消息最大重试次数（超限转死信）。 */
        private int retryMax = 3;
        /** 消息卡死判定闲置阈值（毫秒），触发 XCLAIM 重投。 */
        private long claimIdleMs = 30000;
        /** 是否启用死信队列（后缀 :dlq）。 */
        private boolean deadLetter = true;
        /** 重试扫描间隔（毫秒）。 */
        private long retryIntervalMs = 5000;
    }
}

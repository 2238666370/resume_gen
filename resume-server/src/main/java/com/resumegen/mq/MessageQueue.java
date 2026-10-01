package com.resumegen.mq;

/**
 * 消息队列抽象：供 AI 异步任务、埋点等场景削峰 / 解耦。
 *
 * <p>底层实现通过 {@code mq.type} 配置切换：{@code stream}（Redis Stream，默认）、
 * {@code kafka} / {@code rabbitmq}（预留）、{@code memory}（进程内降级 / 单测）。
 * 使用方仅依赖本接口的 {@link #publish} / {@link #subscribe}，完全不感知底层实现，
 * 切换中间件无需改动任何调用方代码。
 *
 * <p>队列名（如 high/low/stat）由使用方约定，实现仅负责按名投递与消费；
 * 投递语义为 at-least-once，消费方处理需保证幂等。
 */
public interface MessageQueue {

    /** 消息处理器（由订阅方提供）。 */
    interface Handler {
        void handle(String payload);
    }

    /** 投递一条消息到指定队列。 */
    void publish(String queue, String payload);

    /**
     * 订阅指定队列，以 {@code concurrency} 个消费者并发处理消息。
     * 同一队列重复订阅应幂等（仅生效一次）。
     */
    void subscribe(String queue, int concurrency, Handler handler);
}
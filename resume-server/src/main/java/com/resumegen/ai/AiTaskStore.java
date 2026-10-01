package com.resumegen.ai;

/**
 * AI 异步任务登记表抽象：内存 / Redis 双实现，随 mq.type 切换。
 * Redis 实现使任务状态在进程重启后仍可查询（O3）。
 */
public interface AiTaskStore {

    void put(AiTaskRecord task);

    AiTaskRecord get(String taskId);

    void remove(String taskId);
}

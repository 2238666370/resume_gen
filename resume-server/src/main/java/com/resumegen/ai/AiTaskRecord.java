package com.resumegen.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 异步任务登记项（可序列化，供内存 / Redis 双实现持久化）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskRecord {

    private String taskId;
    private String taskType;
    private Long userId;
    private String status;
    private String resultJson;
    private String error;
}

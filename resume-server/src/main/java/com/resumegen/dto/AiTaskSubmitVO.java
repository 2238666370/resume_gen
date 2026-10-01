package com.resumegen.dto;

import lombok.Data;

/**
 * 异步 AI 任务提交结果：仅返回任务凭据与所属队列，实际结果经 {@code GET /api/ai/task/{taskId}} 轮询。
 */
@Data
public class AiTaskSubmitVO {

    private String taskId;

    /** 所属队列（high / low）。 */
    private String queue;

    private String status;
}
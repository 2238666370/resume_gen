package com.resumegen.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 异步 AI 任务查询结果（轮询 {@code GET /api/ai/task/{taskId}} 使用）。
 */
@Data
public class AiTaskVO {

    private String taskId;

    private String taskType;

    /** PENDING / RUNNING / SUCCESS / FAILED。 */
    private String status;

    /** 成功后的结果负载（结构同同步接口 data）。 */
    private JsonNode result;

    /** 失败原因。 */
    private String error;
}
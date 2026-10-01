package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 生成记录（R5/R6 共用，可追溯）。
 */
@Data
@TableName("ai_generation_log")
public class AiGenerationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resumeId;

    /** rewrite / expand / suggest / interview。 */
    private String taskType;

    private String inputHash;

    private String output;

    private Integer tokenUsage;

    private Integer status;

    private LocalDateTime createdAt;
}
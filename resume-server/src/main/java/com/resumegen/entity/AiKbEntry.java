package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识库条目（RAG 用）。向量由 VectorStore 托管，MySQL 仅存元数据与原文。
 */
@Data
@TableName("ai_kb_entry")
public class AiKbEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** interview_q / jd / resume_sample / writing_style / skill_prompt。 */
    private String kbType;

    /** 文档唯一键（skill_prompt 时为技能编码）。 */
    private String code;

    private String title;

    private String content;

    /** 标签/岗位/难度/技术栈等，JSON 字符串。 */
    private String metadata;

    /** 1启用 0禁用。 */
    private Integer status;

    private LocalDateTime createdAt;
}
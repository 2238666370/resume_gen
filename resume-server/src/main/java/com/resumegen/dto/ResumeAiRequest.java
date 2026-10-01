package com.resumegen.dto;

import lombok.Data;

/**
 * AI 简历操作请求（rewrite/expand 用 text+section；suggest 用 resumeId）。
 */
@Data
public class ResumeAiRequest {

    /** 简历 id（suggest 必填；rewrite/expand 可空）。 */
    private String resumeId;

    /** 待处理片段（rewrite/expand 必填）。 */
    private String text;

    /** 所属栏目（如 experience.projects、personal.summary，用于 prompt 与建议定位）。 */
    private String section;

    /** 目标岗位（可选，辅助润色语境）。 */
    private String targetRole;
}
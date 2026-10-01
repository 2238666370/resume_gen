package com.resumegen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 简历评分 + JD 匹配度请求。
 */
@Data
public class ResumeScoreRequest {

    /** 简历 id（必填，字符串形式的数字 id）。 */
    @NotBlank(message = "简历 id 不能为空")
    private String resumeId;

    /** 目标岗位（可选）。 */
    private String targetRole;

    /** 岗位 JD 原文（可选，提供则计算匹配度）。 */
    private String jd;
}

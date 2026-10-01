package com.resumegen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 生成面试题库请求。
 */
@Data
public class InterviewGenerateRequest {

    /** 简历 id（必填，字符串形式的数字 id）。 */
    @NotBlank(message = "简历 id 不能为空")
    private String resumeId;

    /** 目标岗位（可选）。 */
    private String targetRole;

    /** 岗位 JD 原文（可选）。 */
    private String jd;
}
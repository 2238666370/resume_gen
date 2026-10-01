package com.resumegen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 采纳合并（apply）：字段级 diff + 乐观锁回写。
 */
@Data
public class ResumeApplyRequest {

    @NotBlank(message = "简历 id 不能为空")
    private String resumeId;

    /** 乐观锁版本；不一致返回 409。 */
    private Integer version;

    /** 待合并的字段级 patch（仅含目标字段，防止误删其它数据）。 */
    private Map<String, Object> patch;
}
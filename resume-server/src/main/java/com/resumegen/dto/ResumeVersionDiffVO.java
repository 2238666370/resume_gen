package com.resumegen.dto;

import lombok.Data;

/**
 * 两个版本字段级 diff 的返回：两侧完整内容，前端复用 {@code resumeDiff} 渲染高亮。
 */
@Data
public class ResumeVersionDiffVO {

    private int fromVersion;
    private int toVersion;
    private ResumeDTO from;
    private ResumeDTO to;
}

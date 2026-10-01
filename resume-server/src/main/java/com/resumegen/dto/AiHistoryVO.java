package com.resumegen.dto;

import lombok.Data;

/**
 * AI 产出历史记录视图。
 */
@Data
public class AiHistoryVO {

    private String id;
    private String resumeId;
    private String resumeTitle;
    /** rewrite / expand / suggest / interview / improve。 */
    private String taskType;
    /** 输出摘要（列表用，截断）。 */
    private String summary;
    /** 完整输出（详情用）。 */
    private String output;
    private Integer tokenUsage;
    private String createdAt;
}
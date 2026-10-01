package com.resumegen.dto;

import lombok.Data;

/**
 * 全文改进建议条目。
 */
@Data
public class ResumeSuggestionVO {

    private String section;
    private String issue;
    private String advice;
    private String priority;
}
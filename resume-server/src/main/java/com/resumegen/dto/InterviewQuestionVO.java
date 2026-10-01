package com.resumegen.dto;

import lombok.Data;

/**
 * 单条面试题（也是 LLM 结构化输出的元素类型）。
 */
@Data
public class InterviewQuestionVO {

    private String category;
    private String question;
    private String answer;
    private String tips;
}
package com.resumegen.dto;

import lombok.Data;

import java.util.List;

/**
 * 面试题集视图。
 */
@Data
public class InterviewSetVO {

    private String id;
    private String resumeId;
    private String title;
    private String targetRole;
    private List<InterviewQuestionVO> questions;
    private String createdAt;
}
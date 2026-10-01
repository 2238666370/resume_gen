package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试题集（R5）。questions 列存结构化题库 JSON。
 */
@Data
@TableName("interview_set")
public class InterviewSet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resumeId;

    private String title;

    private String targetRole;

    /** 结构化题库 JSON：[{category, question, answer, tips}]。 */
    private String questions;

    private LocalDateTime createdAt;
}
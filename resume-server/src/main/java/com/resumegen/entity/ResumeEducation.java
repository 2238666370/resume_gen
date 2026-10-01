package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resume_education")
public class ResumeEducation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long userId;
    private String school;
    private String degree;
    private String major;
    private String startDate;
    private String endDate;
    private String gpa;
    private String description;
    private Integer sortOrder;
}
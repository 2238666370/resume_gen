package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resume_experience")
public class ResumeExperience {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long userId;
    private String itemType;
    private String company;
    private String position;
    private String startDate;
    private String endDate;
    private Boolean current;
    private String description;
    private Integer sortOrder;
}
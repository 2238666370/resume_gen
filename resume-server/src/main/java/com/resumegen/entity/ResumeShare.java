package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历定点分享（key / 短链接）。
 */
@Data
@TableName("resume_share")
public class ResumeShare {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long resumeId;
    private String shareKey;
    private String password;
    private LocalDateTime expireAt;
    private Integer showContact;
    private Long viewCount;
    private Integer status;
    private LocalDateTime createdAt;
}
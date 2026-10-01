package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社区举报记录。
 */
@Data
@TableName("community_report")
public class CommunityReport {

    public static final int ST_PENDING = 0;
    public static final int ST_RESOLVED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private String reason;
    private Integer status;
    private LocalDateTime createdAt;
}
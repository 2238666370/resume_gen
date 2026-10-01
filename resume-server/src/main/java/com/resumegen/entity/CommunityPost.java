package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社区帖子（先审后发 + 高热度复审）。
 */
@Data
@TableName("community_post")
public class CommunityPost {

    /** 审核状态常量：待审 */
    public static final int ST_PENDING = 0;
    /** 审核状态常量：已上线 */
    public static final int ST_ONLINE = 1;
    /** 审核状态常量：拒绝 */
    public static final int ST_REJECTED = 2;
    /** 审核状态常量：人工复审 */
    public static final int ST_REVIEW = 3;
    /** 审核状态常量：下架 */
    public static final int ST_OFFLINE = 4;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long resumeId;
    private String title;
    private String summary;
    private String tags;
    private String coverUrl;
    private Integer auditStatus;
    private String auditReason;
    private Long likeCount;
    private Long collectCount;
    private Long commentCount;
    private Long viewCount;
    private Integer reportCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
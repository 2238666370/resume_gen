package com.resumegen.dto;

import lombok.Data;

/**
 * 管理端社区帖子项（含审核状态与举报数）。
 */
@Data
public class AdminPostItemVO {

    private String id;
    private String title;
    private String authorId;
    private String authorNickname;
    private int auditStatus;
    private String auditReason;
    private long likeCount;
    private long collectCount;
    private long commentCount;
    private long viewCount;
    private int reportCount;
    private String createdAt;
}
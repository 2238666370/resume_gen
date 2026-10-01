package com.resumegen.dto;

import lombok.Data;

/**
 * 管理端举报项。
 */
@Data
public class AdminReportItemVO {

    private String id;
    private String postId;
    private String postTitle;
    private String reporterId;
    private String reporterNickname;
    private String reason;
    private int status;
    private String createdAt;
}
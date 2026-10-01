package com.resumegen.dto;

import lombok.Data;

/**
 * 分享信息返回（登录用户视角）。
 */
@Data
public class ShareVO {

    private String id;
    private String shareKey;
    private String url;
    private boolean hasPassword;
    private boolean showContact;
    private String expireAt;
    private long viewCount;
    private int status;
    private String createdAt;
}
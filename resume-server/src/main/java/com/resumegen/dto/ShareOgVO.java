package com.resumegen.dto;

import lombok.Data;

/**
 * 分享页 OpenGraph / 社交卡片元信息（脱敏，不含联系方式）。
 */
@Data
public class ShareOgVO {

    private String title;
    private String description;
    private String image;
    private String url;
    private String type = "profile";
    private String siteName;
}

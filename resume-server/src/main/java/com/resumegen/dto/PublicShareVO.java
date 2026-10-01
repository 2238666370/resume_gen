package com.resumegen.dto;

import lombok.Data;

/**
 * 免登录只读分享返回：脱敏后的简历 + 元信息。
 */
@Data
public class PublicShareVO {

    private ResumeDetailVO resume;
    private boolean showContact;
    private long viewCount;
}
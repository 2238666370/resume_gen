package com.resumegen.dto;

import lombok.Data;

/**
 * 简历列表项（精简，不含明细）。
 */
@Data
public class ResumeListItemVO {

    private String id;
    private String title;
    private String templateId;
    private String updatedAt;
}
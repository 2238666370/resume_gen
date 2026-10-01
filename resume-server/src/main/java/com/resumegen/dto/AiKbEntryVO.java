package com.resumegen.dto;

import lombok.Data;

/**
 * AI 知识库条目视图（管理端）。
 */
@Data
public class AiKbEntryVO {

    private String id;
    private String kbType;
    private String code;
    private String title;
    private String content;
    private String metadata;
    private Integer status;
    private String createdAt;
}
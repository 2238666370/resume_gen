package com.resumegen.dto;

import lombok.Data;

/**
 * 简历模板 VO（公开/管理/用户通用）。
 */
@Data
public class TemplateVO {

    private String id;
    private String code;
    private String name;
    private String type;
    private String category;
    /** 渲染 Schema（JSON 字符串），可能为 null。 */
    private String schema;
    private String thumbnail;
    private String ownerUserId;
    private Integer version;
    private Integer schemaVersion;
    private Long useCount;
    private Long viewCount;
    private String auditReason;
    private String publishedAt;
    private Integer sortOrder;
    private Integer status;
    private String createdAt;
}
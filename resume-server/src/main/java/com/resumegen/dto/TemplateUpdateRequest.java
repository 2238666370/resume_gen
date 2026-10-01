package com.resumegen.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端更新模板请求（含上下架，所有字段可选，按非空局部更新）。
 */
@Data
public class TemplateUpdateRequest {

    @Size(max = 100, message = "模板名称过长")
    private String name;

    @Size(max = 20, message = "模板类型过长")
    private String type;

    @Size(max = 50, message = "风格分类过长")
    private String category;

    @Size(max = 500, message = "缩略图URL过长")
    private String thumbnail;

    /** 渲染 Schema（JSON 字符串，可选）。 */
    @Size(max = 50000, message = "Schema过长")
    private String schema;

    private Integer sortOrder;

    /** 1上架 0下架 2待审 3草稿 */
    private Integer status;

    private String auditReason;
}
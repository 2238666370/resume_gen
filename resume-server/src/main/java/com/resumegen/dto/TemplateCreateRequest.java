package com.resumegen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端新增模板请求（code + name 必填）。
 */
@Data
public class TemplateCreateRequest {

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 50, message = "模板编码过长")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "模板编码仅支持字母、数字、下划线、连字符")
    private String code;

    @NotBlank(message = "模板名称不能为空")
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
}
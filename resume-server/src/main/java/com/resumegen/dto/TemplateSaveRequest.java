package com.resumegen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户自定义模板保存请求（创建/更新共用，schema 必填）。
 */
@Data
public class TemplateSaveRequest {

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称过长")
    private String name;

    @Size(max = 50, message = "风格分类过长")
    private String category;

    @NotBlank(message = "模板 Schema 不能为空")
    @Size(max = 50000, message = "Schema过长")
    private String schema;

    /** 更新时携带乐观锁版本号。 */
    private Integer version;
}

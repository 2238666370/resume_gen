package com.resumegen.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeCreateRequest {

    @Size(max = 100, message = "简历名称不能超过 100 字符")
    private String title = "未命名简历";

    /** 可选：创建时指定模板编码（模板市场「一键用」）。 */
    @Size(max = 50, message = "模板编码过长")
    private String templateId;
}
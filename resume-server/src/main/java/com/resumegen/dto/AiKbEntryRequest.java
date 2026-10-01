package com.resumegen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 知识库条目维护请求（管理端）。
 */
@Data
public class AiKbEntryRequest {

    @NotBlank(message = "kbType 不能为空")
    @Size(max = 32, message = "kbType 过长")
    private String kbType;

    @Size(max = 64, message = "code 过长")
    private String code;

    @Size(max = 200, message = "title 过长")
    private String title;

    @NotBlank(message = "content 不能为空")
    private String content;

    /** 标签/岗位/难度/技术栈等，JSON 字符串。 */
    private String metadata;

    /** 1启用 0禁用，缺省 1。 */
    private Integer status;
}
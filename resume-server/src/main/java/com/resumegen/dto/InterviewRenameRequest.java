package com.resumegen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 题集重命名请求。
 */
@Data
public class InterviewRenameRequest {

    @NotBlank(message = "题集名称不能为空")
    @Size(max = 100, message = "题集名称不能超过 100 字")
    private String title;
}
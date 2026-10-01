package com.resumegen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 社区帖子发布请求。
 */
@Data
public class PostPublishRequest {

    @NotNull(message = "请选择要发布的简历")
    private Long resumeId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题过长")
    @Pattern(regexp = ResumeDTO.NO_XSS_PATTERN, message = "标题包含非法内容")
    private String title;

    @Size(max = 500, message = "摘要过长")
    @Pattern(regexp = ResumeDTO.NO_XSS_PATTERN, message = "摘要包含非法内容")
    private String summary;

    @Size(max = 500, message = "封面地址过长")
    private String coverUrl;

    private List<@Size(max = 20, message = "标签过长") String> tags;
}
package com.resumegen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论请求（parentId 为空表示楼顶评论，否则为回复）。
 */
@Data
public class CommentRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容过长")
    @Pattern(regexp = ResumeDTO.NO_XSS_PATTERN, message = "评论包含非法内容")
    private String content;

    private Long parentId;
}
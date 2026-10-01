package com.resumegen.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区帖子详情：公开信息 + 脱敏简历。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostDetailVO extends PostVO {

    private ResumeDetailVO resume;
}
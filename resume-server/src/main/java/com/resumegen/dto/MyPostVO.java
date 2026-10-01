package com.resumegen.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我发布的帖子（含审核状态/原因，作者视角）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyPostVO extends PostVO {

    private int auditStatus;
    private String auditReason;
}
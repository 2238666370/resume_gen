package com.resumegen.dto;

import lombok.Data;

/**
 * 当前用户对某帖子的互动状态与计数。
 */
@Data
public class InteractionVO {

    private boolean liked;
    private boolean collected;
    private long likeCount;
    private long collectCount;
}
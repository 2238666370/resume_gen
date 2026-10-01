package com.resumegen.dto;

import lombok.Data;

/**
 * 单份简历的数据统计（R9）。
 */
@Data
public class MyResumeStatVO {

    private String id;
    private String title;
    private String updatedAt;
    private long pv;
    private long uv;
    private long likeCount;
    private long collectCount;
    private long commentCount;
    private long shareViews;
}

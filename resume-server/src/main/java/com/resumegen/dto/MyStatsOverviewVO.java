package com.resumegen.dto;

import lombok.Data;

/**
 * 我的数据概览（R9）：简历数、观阅、互动、分享访问量。
 */
@Data
public class MyStatsOverviewVO {

    private long resumeCount;
    private long pv;
    private long uv;
    private long likeCount;
    private long collectCount;
    private long commentCount;
    private long shareViews;
}

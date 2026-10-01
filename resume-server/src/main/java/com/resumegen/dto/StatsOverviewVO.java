package com.resumegen.dto;

import lombok.Data;

/**
 * 管理后台概览卡指标。
 */
@Data
public class StatsOverviewVO {

    private long userCount;
    private long resumeCount;
    private long todayPv;
    private long todayUv;
    /** 近 5 分钟活跃访客数（按 user/device 去重）。 */
    private long onlineUsers;
}
package com.resumegen.dto;

import lombok.Data;

/**
 * 趋势序列点（按分钟/小时/天聚合后的 PV/UV）。
 */
@Data
public class TrendPointVO {

    private String time;
    private long pv;
    private long uv;
}
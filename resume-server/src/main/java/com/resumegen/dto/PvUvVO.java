package com.resumegen.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 区间 PV/UV 汇总。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PvUvVO {

    private long pv;
    private long uv;
}
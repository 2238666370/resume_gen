package com.resumegen.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface StatMinuteMapper {

    /** 分钟级 PV 预聚合：幂等 upsert（同分钟重跑只 +1）。 */
    @Insert("INSERT INTO stat_minute (stat_time, metric, dimension, value) VALUES (#{t}, 'pv', '', 1) "
            + "ON DUPLICATE KEY UPDATE value = value + 1")
    int upsertPv(@Param("t") LocalDateTime statTime);
}
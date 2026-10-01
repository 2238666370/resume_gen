package com.resumegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.resumegen.dto.TrendPointVO;
import com.resumegen.entity.AccessLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessLogMapper extends BaseMapper<AccessLog> {

    @Select("SELECT COUNT(*) FROM access_log WHERE ts >= #{from} AND ts < #{to}")
    long countPv(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("SELECT COUNT(DISTINCT CASE WHEN user_id IS NOT NULL THEN CONCAT('u:', user_id) "
            + "ELSE CONCAT('d:', device_id) END) FROM access_log "
            + "WHERE ts >= #{from} AND ts < #{to}")
    long countUv(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("SELECT DATE_FORMAT(ts, #{fmt}) AS time, COUNT(*) AS pv, "
            + "COUNT(DISTINCT CASE WHEN user_id IS NOT NULL THEN CONCAT('u:', user_id) "
            + "ELSE CONCAT('d:', device_id) END) AS uv "
            + "FROM access_log WHERE ts >= #{from} AND ts < #{to} "
            + "GROUP BY DATE_FORMAT(ts, #{fmt}) ORDER BY time")
    List<TrendPointVO> trend(@Param("fmt") String fmt,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);

    @Select("<script>SELECT COUNT(*) FROM access_log WHERE resume_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    long countPvByResumes(@Param("ids") List<Long> ids);

    @Select("<script>SELECT COUNT(DISTINCT CASE WHEN user_id IS NOT NULL THEN CONCAT('u:', user_id) "
            + "ELSE CONCAT('d:', device_id) END) FROM access_log WHERE resume_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    long countUvByResumes(@Param("ids") List<Long> ids);

    @Select("SELECT DATE_FORMAT(ts, #{fmt}) AS time, COUNT(*) AS pv, "
            + "COUNT(DISTINCT CASE WHEN user_id IS NOT NULL THEN CONCAT('u:', user_id) "
            + "ELSE CONCAT('d:', device_id) END) AS uv "
            + "FROM access_log WHERE resume_id = #{resumeId} AND ts >= #{from} AND ts < #{to} "
            + "GROUP BY DATE_FORMAT(ts, #{fmt}) ORDER BY time")
    List<TrendPointVO> trendByResume(@Param("resumeId") Long resumeId,
                                     @Param("fmt") String fmt,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}
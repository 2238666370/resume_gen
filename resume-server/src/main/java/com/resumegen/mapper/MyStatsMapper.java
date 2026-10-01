package com.resumegen.mapper;

import com.resumegen.dto.MyResumeStatVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 我的数据后台（R9）聚合查询。
 */
public interface MyStatsMapper {

    /** 按简历维度统计：观阅 PV/UV、社区点赞/收藏/评论、分享访问量（join + group by 单查，避免 N 子查询）。 */
    @Select("SELECT CAST(r.id AS CHAR) AS id, r.title AS title, "
            + "DATE_FORMAT(r.updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt, "
            + "COALESCE(a.pv, 0) AS pv, COALESCE(a.uv, 0) AS uv, "
            + "COALESCE(p.likeCount, 0) AS likeCount, COALESCE(p.collectCount, 0) AS collectCount, "
            + "COALESCE(p.commentCount, 0) AS commentCount, COALESCE(s.shareViews, 0) AS shareViews "
            + "FROM resume r "
            + "LEFT JOIN (SELECT resume_id, COUNT(*) AS pv, "
            + "  COUNT(DISTINCT CASE WHEN user_id IS NOT NULL THEN CONCAT('u:', user_id) "
            + "  ELSE CONCAT('d:', device_id) END) AS uv FROM access_log GROUP BY resume_id) a ON a.resume_id = r.id "
            + "LEFT JOIN (SELECT resume_id, COALESCE(SUM(like_count),0) AS likeCount, "
            + "  COALESCE(SUM(collect_count),0) AS collectCount, "
            + "  COALESCE(SUM(comment_count),0) AS commentCount FROM community_post GROUP BY resume_id) p ON p.resume_id = r.id "
            + "LEFT JOIN (SELECT resume_id, COALESCE(SUM(view_count),0) AS shareViews FROM resume_share GROUP BY resume_id) s ON s.resume_id = r.id "
            + "WHERE r.user_id = #{userId} AND r.status = 1 "
            + "ORDER BY r.updated_at DESC")
    List<MyResumeStatVO> listResumeStats(@Param("userId") Long userId);
}

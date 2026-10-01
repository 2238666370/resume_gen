package com.resumegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.resumegen.entity.CommunityPost;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface CommunityPostMapper extends BaseMapper<CommunityPost> {

    @Update("UPDATE community_post SET like_count = like_count + 1 WHERE id = #{id} AND like_count >= 0")
    int incrLike(@Param("id") Long id);

    @Update("UPDATE community_post SET like_count = like_count - 1 WHERE id = #{id} AND like_count > 0")
    int decrLike(@Param("id") Long id);

    @Update("UPDATE community_post SET collect_count = collect_count + 1 WHERE id = #{id} AND collect_count >= 0")
    int incrCollect(@Param("id") Long id);

    @Update("UPDATE community_post SET collect_count = collect_count - 1 WHERE id = #{id} AND collect_count > 0")
    int decrCollect(@Param("id") Long id);

    @Update("UPDATE community_post SET comment_count = comment_count + 1 WHERE id = #{id} AND comment_count >= 0")
    int incrComment(@Param("id") Long id);

    @Update("UPDATE community_post SET comment_count = comment_count - 1 WHERE id = #{id} AND comment_count > 0")
    int decrComment(@Param("id") Long id);

    @Update("UPDATE community_post SET view_count = view_count + 1 WHERE id = #{id}")
    int incrView(@Param("id") Long id);

    @Update("UPDATE community_post SET report_count = report_count + 1 WHERE id = #{id}")
    int incrReport(@Param("id") Long id);

    @Select("<script>SELECT COALESCE(SUM(like_count), 0) FROM community_post WHERE resume_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    long sumLikeByResumes(@Param("ids") List<Long> ids);

    @Select("<script>SELECT COALESCE(SUM(collect_count), 0) FROM community_post WHERE resume_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    long sumCollectByResumes(@Param("ids") List<Long> ids);

    @Select("<script>SELECT COALESCE(SUM(comment_count), 0) FROM community_post WHERE resume_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    long sumCommentByResumes(@Param("ids") List<Long> ids);
}
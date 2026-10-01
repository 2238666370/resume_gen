package com.resumegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.resumegen.entity.ResumeShare;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ResumeShareMapper extends BaseMapper<ResumeShare> {

    /** 原子递增访问次数。 */
    @Update("UPDATE resume_share SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    /** 我的分享总访问量。 */
    @Select("SELECT COALESCE(SUM(view_count), 0) FROM resume_share WHERE user_id = #{userId}")
    long sumViewCountByUser(@Param("userId") Long userId);
}
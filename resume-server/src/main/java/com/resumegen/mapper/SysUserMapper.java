package com.resumegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.resumegen.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 原子递增登录令牌版本号，用于单会话踢下线。 */
    @Update("UPDATE sys_user SET token_version = token_version + 1 WHERE id = #{id}")
    int incrementTokenVersion(@Param("id") Long id);

    /** 更新用户个人信息（仅涉及个人档案列，不触碰登录/状态等字段）。 */
    @Update("UPDATE sys_user SET name = #{name}, title = #{title}, phone = #{phone}, email = #{email}, "
            + "location = #{location}, website = #{website}, avatar = #{avatar}, summary = #{summary} "
            + "WHERE id = #{id}")
    int updateProfile(SysUser user);
}
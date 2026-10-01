package com.resumegen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String email;
    /** 真实姓名 */
    private String name;
    /** 求职意向/头衔 */
    private String title;
    private String phone;
    private String location;
    private String website;
    private String avatar;
    /** 个人简介 */
    private String summary;
    private String role;
    private Integer status;
    private Long tokenVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
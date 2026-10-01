package com.resumegen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户个人信息编辑请求。
 */
@Data
public class ProfileRequest {

    @Size(max = 50, message = "姓名长度不能超过 50")
    @Pattern(regexp = ResumeDTO.NO_XSS_PATTERN, message = "姓名包含非法内容")
    private String name;

    @Size(max = 100, message = "求职意向长度不能超过 100")
    @Pattern(regexp = ResumeDTO.NO_XSS_PATTERN, message = "求职意向包含非法内容")
    private String title;

    @Size(max = 30, message = "电话长度不能超过 30")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    @Size(max = 100, message = "所在地长度不能超过 100")
    @Pattern(regexp = ResumeDTO.NO_XSS_PATTERN, message = "所在地包含非法内容")
    private String location;

    @Size(max = 200, message = "个人主页长度不能超过 200")
    @Pattern(regexp = ResumeDTO.NO_XSS_PATTERN, message = "个人主页包含非法内容")
    private String website;

    @Size(max = 500, message = "头像长度不能超过 500")
    @Pattern(regexp = ResumeDTO.NO_XSS_PATTERN, message = "头像包含非法内容")
    private String avatar;

    @Pattern(regexp = ResumeDTO.NO_XSS_PATTERN, message = "个人简介包含非法内容")
    private String summary;
}
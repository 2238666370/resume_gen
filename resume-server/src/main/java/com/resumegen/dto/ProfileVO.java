package com.resumegen.dto;

import lombok.Data;

/**
 * 用户个人信息返回。
 */
@Data
public class ProfileVO {

    private String name;
    private String title;
    private String phone;
    private String email;
    private String location;
    private String website;
    private String avatar;
    private String summary;
}
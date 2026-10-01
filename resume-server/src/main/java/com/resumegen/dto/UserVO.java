package com.resumegen.dto;

import lombok.Data;

@Data
public class UserVO {

    private String id;
    private String username;
    private String role;
    private String nickname;
    private String email;
    private Integer status;
    private String createdAt;
}
package com.resumegen.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private long expiresIn;
    private UserVO user;
}
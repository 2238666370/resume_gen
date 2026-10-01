package com.resumegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long expireSeconds = 86400;
    private String header = "Authorization";
    private String tokenPrefix = "Bearer ";
}
package com.resumegen.security;

/**
 * 从 JWT 解析出的登录态。
 */
public record LoginUser(Long userId, String role, Long ver) {
}
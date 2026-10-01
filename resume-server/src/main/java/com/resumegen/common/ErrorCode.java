package com.resumegen.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或登录已失效"),
    KICKED_OUT(401, "账号已在其他设备登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    RATE_LIMITED(429, "请求过于频繁，请稍后重试"),
    SERVER_ERROR(500, "服务器繁忙");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
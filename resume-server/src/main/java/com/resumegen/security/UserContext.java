package com.resumegen.security;

import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;

/**
 * 请求级登录态上下文（ThreadLocal）。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.userId();
    }

    /**
     * 取当前用户 id；未登录直接抛 401。
     * 写操作用它替代 {@link #userId()}，避免「无登录态时把 owner 写成 NULL」产生脏数据。
     */
    public static long requireUserId() {
        Long id = userId();
        if (id == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    public static String role() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.role();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(role());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
package com.resumegen.security;

import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：解析 JWT → 校验单会话版本 → 写入 UserContext。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;
    private final JwtProperties jwtProperties;

    public AuthInterceptor(JwtUtil jwtUtil, SessionService sessionService, JwtProperties jwtProperties) {
        this.jwtUtil = jwtUtil;
        this.sessionService = sessionService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(jwtProperties.getHeader());
        String token;
        if (header != null && header.startsWith(jwtProperties.getTokenPrefix())) {
            token = header.substring(jwtProperties.getTokenPrefix().length()).trim();
        } else {
            // SSE（EventSource）无法自定义请求头，允许通过 ?token= 查询参数传递
            token = request.getParameter("token");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        LoginUser user;
        try {
            user = jwtUtil.parse(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "登录已过期");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        long currentVer = sessionService.currentVersion(user.userId());
        if (user.ver() != currentVer) {
            throw new BusinessException(ErrorCode.KICKED_OUT);
        }

        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
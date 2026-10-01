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
 *
 * <p>公开只读接口（如模板列表/市场/详情）允许匿名访问，但**必须按方法区分**：
 * Spring 的 {@code excludePathPatterns} 只按路径匹配、不区分 HTTP 方法，
 * 若用它排除 {@code /api/templates}，会把 {@code POST /api/templates}（新建用户模板）
 * 一并放行，导致 UserContext 为空、模板 owner 写成 NULL，用户「保存后在我的模板里查不到」。
 * 因此这里对公开只读路径做「仅 GET 才放行」的判定。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 允许匿名访问的公开只读路径；以 {@code /} 结尾表示前缀匹配。 */
    private static final String[] ANONYMOUS_GET_PATHS = {
            "/api/templates",
            "/api/templates/detail/",
            "/api/templates/market",
            "/api/templates/market/",
    };

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;
    private final JwtProperties jwtProperties;

    public AuthInterceptor(JwtUtil jwtUtil, SessionService sessionService, JwtProperties jwtProperties) {
        this.jwtUtil = jwtUtil;
        this.sessionService = sessionService;
        this.jwtProperties = jwtProperties;
    }

    /** 该请求是否属于「公开只读」：必须是 GET，且路径命中白名单。 */
    private boolean allowAnonymousGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        for (String path : ANONYMOUS_GET_PATHS) {
            boolean matched = path.endsWith("/") ? uri.startsWith(path) : uri.equals(path);
            if (matched) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        boolean anonymousAllowed = allowAnonymousGet(request);

        String header = request.getHeader(jwtProperties.getHeader());
        String token;
        if (header != null && header.startsWith(jwtProperties.getTokenPrefix())) {
            token = header.substring(jwtProperties.getTokenPrefix().length()).trim();
        } else {
            // SSE（EventSource）无法自定义请求头，允许通过 ?token= 查询参数传递
            token = request.getParameter("token");
        }
        if (token == null || token.isBlank()) {
            if (anonymousAllowed) {
                return true;
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        LoginUser user;
        try {
            user = jwtUtil.parse(token);
        } catch (ExpiredJwtException e) {
            if (anonymousAllowed) {
                return true;
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "登录已过期");
        } catch (Exception e) {
            if (anonymousAllowed) {
                return true;
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        long currentVer = sessionService.currentVersion(user.userId());
        if (user.ver() != currentVer) {
            // 公开只读接口不因被踢下线而失败，降级为匿名访问
            if (anonymousAllowed) {
                return true;
            }
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
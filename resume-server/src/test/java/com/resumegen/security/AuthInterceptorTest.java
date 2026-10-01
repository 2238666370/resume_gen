package com.resumegen.security;

import com.resumegen.common.BusinessException;
import com.resumegen.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 认证拦截器测试。
 *
 * <p>重点锁定回归点：模板的「公开只读」必须**按 HTTP 方法**放行。
 * 曾经用 {@code excludePathPatterns("/api/templates")} 排除，导致
 * {@code POST /api/templates}（新建用户模板）也被放行，UserContext 为空、
 * 模板 owner 落成 NULL，用户保存后在「我的模板」里查不到。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private SessionService sessionService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthInterceptor(jwtUtil, sessionService, jwtProperties);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 构造匿名请求（无 Authorization 头）。
     * 仅 GET 会读取 requestURI（非 GET 在 allowAnonymousGet 中早返回），避免多余 stub。
     */
    private void anonymous(String method, String uri) {
        when(request.getMethod()).thenReturn(method);
        if ("GET".equalsIgnoreCase(method)) {
            when(request.getRequestURI()).thenReturn(uri);
        }
        when(request.getHeader("Authorization")).thenReturn(null);
        when(jwtProperties.getHeader()).thenReturn("Authorization");
    }

    @Test
    void anonymousGetOnTemplateListIsAllowed() {
        anonymous("GET", "/api/templates");
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void anonymousGetOnTemplateMarketIsAllowed() {
        anonymous("GET", "/api/templates/market");
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void anonymousGetOnTemplateDetailIsAllowed() {
        anonymous("GET", "/api/templates/detail/classic");
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    /** 回归护栏：新建用户模板必须要求登录。 */
    @Test
    void anonymousPostOnTemplateCreateIsRejected() {
        anonymous("POST", "/api/templates");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void anonymousGetOnMyTemplatesIsRejected() {
        anonymous("GET", "/api/templates/my");
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validTokenPopulatesUserContext() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer tk");
        when(jwtProperties.getHeader()).thenReturn("Authorization");
        when(jwtProperties.getTokenPrefix()).thenReturn("Bearer ");
        when(jwtUtil.parse("tk")).thenReturn(new LoginUser(7L, "USER", 1L));
        when(sessionService.currentVersion(7L)).thenReturn(1L);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(UserContext.userId()).isEqualTo(7L);
    }
}

package com.resumegen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.GlobalExceptionHandler;
import com.resumegen.config.JwtProperties;
import com.resumegen.security.AuthInterceptor;
import com.resumegen.security.JwtUtil;
import com.resumegen.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.HandlerInterceptor;

import static org.mockito.Mockito.when;

/**
 * 独立 MockMvc 基类：手动装配控制器 + 真实 JwtUtil/拦截器 + 全局异常处理 + 校验器，
 * 避免加载完整 Spring 上下文（无需 MySQL/Redis）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class BaseMockMvcTest {

    @Mock
    protected SessionService sessionService;

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected JwtProperties jwtProperties;
    protected JwtUtil jwtUtil;
    protected MockMvc mockMvc;

    @BeforeEach
    void setUpSecurity() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-only-secret-key-minimum-32-bytes-long-ok!");
        jwtProperties.setExpireSeconds(3600);
        jwtProperties.setHeader("Authorization");
        jwtProperties.setTokenPrefix("Bearer ");
        jwtUtil = new JwtUtil(jwtProperties);
    }

    protected void buildMockMvc(Object... controllers) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        AuthInterceptor authInterceptor = new AuthInterceptor(jwtUtil, sessionService, jwtProperties);
        // 复刻 WebConfig 的放行规则：注册/登录不需要 Token
        HandlerInterceptor guarded = new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String uri = request.getRequestURI();
                if (uri.endsWith("/api/auth/register") || uri.endsWith("/api/auth/login")
                        || uri.endsWith("/api/auth/captcha") || uri.startsWith("/api/public/")
                        || uri.equals("/api/templates") || uri.startsWith("/api/templates/")) {
                    return true;
                }
                return authInterceptor.preHandle(request, response, handler);
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                authInterceptor.afterCompletion(request, response, handler, ex);
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(guarded)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    /** 生成合法 Token 并 stub 对应会话版本，使拦截器放行。 */
    protected String tokenFor(Long userId, String role, long ver) {
        when(sessionService.currentVersion(userId)).thenReturn(ver);
        return jwtUtil.generate(userId, role, ver);
    }
}
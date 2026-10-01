package com.resumegen.config;

import com.resumegen.security.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ResumeProperties props;

    public WebConfig(AuthInterceptor authInterceptor, ResumeProperties props) {
        this.authInterceptor = authInterceptor;
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注意：模板的「公开只读」不能在 excludePathPatterns 里排除
        // （它按路径匹配、不区分方法，会连带放行 POST /api/templates），
        // 改由 AuthInterceptor 内部按「仅 GET」判定，见 ANONYMOUS_GET_PATHS。
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**", "/admin/**")
                .excludePathPatterns("/api/auth/register", "/api/auth/login", "/api/auth/captcha",
                        "/api/public/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = props.getCors().getAllowedOrigins();
        if (origins == null || origins.isBlank()) {
            return;
        }
        String[] arr = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        registry.addMapping("/**")
                .allowedOrigins(arr)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
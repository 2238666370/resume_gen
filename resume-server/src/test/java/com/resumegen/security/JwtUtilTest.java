package com.resumegen.security;

import com.resumegen.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-only-secret-key-minimum-32-bytes-long-ok!");
        props.setExpireSeconds(3600);
        jwtUtil = new JwtUtil(props);
    }

    @Test
    void generateAndParseRoundTrip() {
        String token = jwtUtil.generate(123L, "USER", 5L);

        LoginUser user = jwtUtil.parse(token);

        assertThat(user.userId()).isEqualTo(123L);
        assertThat(user.role()).isEqualTo("USER");
        assertThat(user.ver()).isEqualTo(5L);
    }

    @Test
    void parseHandlesZeroVersion() {
        String token = jwtUtil.generate(1L, "ADMIN", 0L);
        LoginUser user = jwtUtil.parse(token);
        assertThat(user.ver()).isEqualTo(0L);
    }

    @Test
    void tamperedTokenRejected() {
        String token = jwtUtil.generate(1L, "USER", 0L);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtUtil.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenRejected() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-only-secret-key-minimum-32-bytes-long-ok!");
        props.setExpireSeconds(-1); // 已过期
        JwtUtil expiredUtil = new JwtUtil(props);
        String token = expiredUtil.generate(1L, "USER", 0L);

        assertThatThrownBy(() -> jwtUtil.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
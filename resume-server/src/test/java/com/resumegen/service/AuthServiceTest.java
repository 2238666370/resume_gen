package com.resumegen.service;

import com.resumegen.common.BusinessException;
import com.resumegen.dto.LoginRequest;
import com.resumegen.dto.LoginResponse;
import com.resumegen.dto.RegisterRequest;
import com.resumegen.dto.UserVO;
import com.resumegen.entity.SysUser;
import com.resumegen.mapper.SysUserMapper;
import com.resumegen.security.JwtUtil;
import com.resumegen.security.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private SessionService sessionService;
    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerReq(String username) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword("pass123");
        req.setEmail("a@b.c");
        req.setCaptchaId("cid");
        req.setCaptchaCode("1234");
        return req;
    }

    private LoginRequest loginReq(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setCaptchaId("cid");
        req.setCaptchaCode("1234");
        return req;
    }

    private void captchaOk() {
        when(captchaService.verify(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void registerDuplicateThrowsConflict() {
        captchaOk();
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(registerReq("alice")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);
    }

    @Test
    void registerSuccessEncodesPasswordAndReturnsToken() {
        captchaOk();
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userMapper.insert(any())).thenReturn(1);
        when(jwtUtil.generate(any(), any(), any())).thenReturn("token-xyz");
        when(jwtUtil.getExpireSeconds()).thenReturn(86400L);

        LoginResponse resp = authService.register(registerReq("alice"));

        assertThat(resp.getToken()).isEqualTo("token-xyz");
        assertThat(resp.getUser().getUsername()).isEqualTo("alice");
        assertThat(resp.getUser().getRole()).isEqualTo("USER");

        ArgumentCaptor<SysUser> cap = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(cap.capture());
        SysUser saved = cap.getValue();
        assertThat(saved.getPassword()).isEqualTo("hashed");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getTokenVersion()).isEqualTo(0L);
    }

    @Test
    void registerWithWrongCaptchaThrowsBadRequest() {
        when(captchaService.verify(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.register(registerReq("alice")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }

    @Test
    void loginWrongPasswordThrowsBadRequest() {
        captchaOk();
        SysUser u = new SysUser();
        u.setId(1L);
        u.setPassword("hashed");
        when(userMapper.selectOne(any())).thenReturn(u);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginReq("alice", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }

    @Test
    void loginDisabledThrowsForbidden() {
        captchaOk();
        SysUser u = new SysUser();
        u.setId(1L);
        u.setPassword("hashed");
        u.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(u);
        when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginReq("alice", "pass123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
    }

    @Test
    void loginSuccessIncrementsTokenVersionAndKicksOldSession() {
        captchaOk();
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("alice");
        u.setPassword("hashed");
        u.setRole("USER");
        u.setStatus(1);
        u.setTokenVersion(0L);
        when(userMapper.selectOne(any())).thenReturn(u);
        when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);
        when(userMapper.incrementTokenVersion(1L)).thenReturn(1);

        SysUser fresh = new SysUser();
        fresh.setId(1L);
        fresh.setUsername("alice");
        fresh.setRole("USER");
        fresh.setStatus(1);
        fresh.setTokenVersion(1L);
        when(userMapper.selectById(1L)).thenReturn(fresh);

        when(jwtUtil.generate(1L, "USER", 1L)).thenReturn("token-xyz");
        when(jwtUtil.getExpireSeconds()).thenReturn(86400L);

        LoginResponse resp = authService.login(loginReq("alice", "pass123"));

        assertThat(resp.getToken()).isEqualTo("token-xyz");
        assertThat(resp.getExpiresIn()).isEqualTo(86400L);
        assertThat(resp.getUser().getUsername()).isEqualTo("alice");
        verify(userMapper).incrementTokenVersion(1L);
        verify(sessionService).invalidate(1L);
    }

    @Test
    void logoutIncrementsVersionAndInvalidates() {
        authService.logout(1L);
        verify(userMapper).incrementTokenVersion(1L);
        verify(sessionService).invalidate(1L);
    }

    @Test
    void meReturnsUserWithoutPassword() {
        SysUser u = new SysUser();
        u.setId(7L);
        u.setUsername("alice");
        u.setNickname("A");
        u.setRole("USER");
        u.setEmail("a@b.c");
        u.setStatus(1);
        when(userMapper.selectById(7L)).thenReturn(u);

        UserVO vo = authService.me(7L);

        assertThat(vo.getId()).isEqualTo("7");
        assertThat(vo.getUsername()).isEqualTo("alice");
        assertThat(vo.getNickname()).isEqualTo("A");
    }
}
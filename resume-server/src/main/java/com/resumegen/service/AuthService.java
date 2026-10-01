package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.LoginRequest;
import com.resumegen.dto.LoginResponse;
import com.resumegen.dto.RegisterRequest;
import com.resumegen.dto.UserVO;
import com.resumegen.entity.SysUser;
import com.resumegen.mapper.SysUserMapper;
import com.resumegen.security.JwtUtil;
import com.resumegen.security.SessionService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Service
public class AuthService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SessionService sessionService;
    private final CaptchaService captchaService;

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, SessionService sessionService, CaptchaService captchaService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.sessionService = sessionService;
        this.captchaService = captchaService;
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        if (!captchaService.verify(req.getCaptchaId(), req.getCaptchaCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "验证码错误或已过期");
        }
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setNickname(req.getUsername());
        u.setEmail(req.getEmail());
        u.setRole("USER");
        u.setStatus(1);
        u.setTokenVersion(0L);
        userMapper.insert(u);
        // 注册即登录，直接返回令牌
        return buildLoginResponse(u);
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        if (!captchaService.verify(req.getCaptchaId(), req.getCaptchaCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "验证码错误或已过期");
        }
        SysUser u = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (u == null || !passwordEncoder.matches(req.getPassword(), u.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "账号或密码错误");
        }
        if (u.getStatus() == null || u.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "账户已被禁用");
        }
        // 单会话：递增版本号踢掉旧登录
        userMapper.incrementTokenVersion(u.getId());
        sessionService.invalidate(u.getId());
        SysUser fresh = userMapper.selectById(u.getId());

        return buildLoginResponse(fresh);
    }

    private LoginResponse buildLoginResponse(SysUser u) {
        String token = jwtUtil.generate(u.getId(), u.getRole(), u.getTokenVersion());
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setExpiresIn(jwtUtil.getExpireSeconds());
        resp.setUser(toVO(u));
        return resp;
    }

    @Transactional
    public void logout(Long userId) {
        userMapper.incrementTokenVersion(userId);
        sessionService.invalidate(userId);
    }

    public UserVO me(Long userId) {
        return toVO(userMapper.selectById(userId));
    }

    public static UserVO toVO(SysUser u) {
        if (u == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(String.valueOf(u.getId()));
        vo.setUsername(u.getUsername());
        vo.setRole(u.getRole());
        vo.setNickname(u.getNickname());
        vo.setEmail(u.getEmail());
        vo.setStatus(u.getStatus());
        vo.setCreatedAt(u.getCreatedAt() == null ? null : u.getCreatedAt().format(FMT));
        return vo;
    }
}
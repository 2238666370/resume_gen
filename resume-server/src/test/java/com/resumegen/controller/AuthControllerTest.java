package com.resumegen.controller;

import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.CaptchaVO;
import com.resumegen.dto.LoginResponse;
import com.resumegen.dto.UserVO;
import com.resumegen.service.AuthService;
import com.resumegen.service.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends BaseMockMvcTest {

    @Mock
    private AuthService authService;
    @Mock
    private CaptchaService captchaService;

    private static final String REG_BODY =
            "{\"username\":\"alice\",\"password\":\"pass123\",\"email\":\"a@b.c\",\"captchaId\":\"cid\",\"captchaCode\":\"1234\"}";

    @BeforeEach
    void setUp() {
        buildMockMvc(new AuthController(authService, captchaService));
    }

    private LoginResponse loginResponse(String name) {
        UserVO vo = new UserVO();
        vo.setId("1");
        vo.setUsername(name);
        vo.setRole("USER");
        LoginResponse resp = new LoginResponse();
        resp.setToken("token-xyz");
        resp.setExpiresIn(86400);
        resp.setUser(vo);
        return resp;
    }

    @Test
    void registerSuccess() throws Exception {
        when(authService.register(any())).thenReturn(loginResponse("alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REG_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("token-xyz"))
                .andExpect(jsonPath("$.data.user.username").value("alice"));
    }

    @Test
    void registerDuplicateReturns409() throws Exception {
        when(authService.register(any())).thenThrow(new BusinessException(ErrorCode.CONFLICT));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REG_BODY))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void registerMissingCaptchaReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass123\"}"))
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(authService);
    }

    @Test
    void registerInvalidUsernameReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"pass123\",\"captchaId\":\"cid\",\"captchaCode\":\"1234\"}"))
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(authService);
    }

    @Test
    void captchaReturnsBase64Image() throws Exception {
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaId("cid-1");
        vo.setImageBase64("data:image/png;base64,xxx");
        when(captchaService.generate()).thenReturn(vo);

        mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.captchaId").value("cid-1"))
                .andExpect(jsonPath("$.data.imageBase64").value("data:image/png;base64,xxx"));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void meWithTamperedTokenReturns401() throws Exception {
        String token = jwtUtil.generate(1L, "USER", 0L) + "tampered";

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void meWithValidTokenReturnsUser() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        UserVO vo = new UserVO();
        vo.setId("1");
        vo.setUsername("alice");
        vo.setRole("USER");
        when(authService.me(1L)).thenReturn(vo);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void loginDisabledReturns403() throws Exception {
        when(authService.login(any())).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pass123\",\"captchaId\":\"cid\",\"captchaCode\":\"1234\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }
}
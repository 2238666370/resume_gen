package com.resumegen.controller;

import com.resumegen.dto.ProfileVO;
import com.resumegen.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ProfileControllerTest extends BaseMockMvcTest {

    @Mock
    private ProfileService profileService;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new ProfileController(profileService));
    }

    @Test
    void getWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(profileService);
    }

    @Test
    void getReturnsProfile() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        ProfileVO vo = new ProfileVO();
        vo.setPhone("13800000000");
        when(profileService.get(1L)).thenReturn(vo);

        mockMvc.perform(get("/api/profile").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.phone").value("13800000000"));
    }

    @Test
    void updateReturnsProfile() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        ProfileVO vo = new ProfileVO();
        vo.setName("张三");
        when(profileService.update(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(put("/api/profile")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"phone\":\"13800000000\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("张三"));
    }

    @Test
    void updateInvalidSummaryReturns400() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(put("/api/profile")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"<script>alert(1)</script>\"}"))
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(profileService);
    }

    @Test
    void updateXssNameReturns400() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(put("/api/profile")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<script>alert(1)</script>\"}"))
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(profileService);
    }

    @Test
    void updateTooLongNameReturns400() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(put("/api/profile")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(51) + "\"}"))
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(profileService);
    }

    @Test
    void updateInvalidEmailReturns400() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(put("/api/profile")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(profileService);
    }

    @Test
    void updateXssWebsiteReturns400() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(put("/api/profile")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"website\":\"javascript:alert(1)\"}"))
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(profileService);
    }
}
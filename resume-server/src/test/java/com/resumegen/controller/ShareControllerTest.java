package com.resumegen.controller;

import com.resumegen.ai.PublicRateLimiter;
import com.resumegen.dto.PublicShareVO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ShareVO;
import com.resumegen.service.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ShareControllerTest extends BaseMockMvcTest {

    @Mock
    private ShareService shareService;
    @Mock
    private PublicRateLimiter rateLimiter;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new ShareController(shareService, rateLimiter));
    }

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":1}"))
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(shareService);
    }

    @Test
    void createSuccessReturnsShare() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        ShareVO vo = new ShareVO();
        vo.setShareKey("abc12345");
        vo.setUrl("http://localhost:5173/#/s/abc12345");
        when(shareService.create(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(post("/api/shares")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":1}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shareKey").value("abc12345"))
                .andExpect(jsonPath("$.data.url").value("http://localhost:5173/#/s/abc12345"));
    }

    @Test
    void createMissingResumeIdReturns400() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(post("/api/shares")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(shareService);
    }

    @Test
    void listReturnsShares() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        ShareVO vo = new ShareVO();
        vo.setShareKey("abc12345");
        when(shareService.list(1L)).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/shares").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].shareKey").value("abc12345"));
    }

    @Test
    void revokeReturns200() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(delete("/api/shares/abc12345").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void publicShareAccessibleWithoutAuth() throws Exception {
        PublicShareVO vo = new PublicShareVO();
        ResumeDetailVO resume = new ResumeDetailVO();
        resume.setId("3");
        vo.setResume(resume);
        when(shareService.publicRead("abc12345", null)).thenReturn(vo);

        mockMvc.perform(get("/api/public/shares/abc12345"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.resume.id").value("3"));
    }
}
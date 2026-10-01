package com.resumegen.controller;

import com.resumegen.ai.AiAsyncService;
import com.resumegen.ai.AiRateLimiter;
import com.resumegen.dto.ResumeRewriteVO;
import com.resumegen.service.ResumeAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ResumeAiControllerTest extends BaseMockMvcTest {

    @Mock
    private ResumeAiService resumeAiService;

    @Mock
    private AiAsyncService aiAsyncService;

    @Mock
    private AiRateLimiter rateLimiter;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new ResumeAiController(resumeAiService, aiAsyncService, rateLimiter));
    }

    @Test
    void rewriteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/ai/resume/rewrite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(resumeAiService);
    }

    @Test
    void rewriteReturnsRevised() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        ResumeRewriteVO vo = new ResumeRewriteVO();
        vo.setOriginal("原文");
        vo.setRevised("润色后");
        when(resumeAiService.rewrite(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(post("/api/ai/resume/rewrite")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"原文\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.revised").value("润色后"));
    }

    @Test
    void suggestReturns200() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        mockMvc.perform(post("/api/ai/resume/suggest")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"1\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void applyRequiresResumeId() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        mockMvc.perform(post("/api/ai/resume/apply")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patch\":{}}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(resumeAiService);
    }

    @Test
    void scoreWithoutResumeIdReturns400() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        mockMvc.perform(post("/api/ai/resume/score")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(resumeAiService);
    }
}
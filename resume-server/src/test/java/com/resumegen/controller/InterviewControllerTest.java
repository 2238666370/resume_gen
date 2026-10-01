package com.resumegen.controller;

import com.resumegen.ai.AiAsyncService;
import com.resumegen.ai.AiRateLimiter;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.InterviewSetVO;
import com.resumegen.service.InterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class InterviewControllerTest extends BaseMockMvcTest {

    @Mock
    private InterviewService interviewService;

    @Mock
    private AiAsyncService aiAsyncService;

    @Mock
    private AiRateLimiter rateLimiter;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new InterviewController(interviewService, aiAsyncService, rateLimiter));
    }

    @Test
    void generateWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/ai/interview/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"1\"}"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(interviewService);
    }

    @Test
    void generateMissingResumeIdReturns400() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        mockMvc.perform(post("/api/ai/interview/generate")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(interviewService);
    }

    @Test
    void generateReturnsSet() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        InterviewSetVO vo = new InterviewSetVO();
        vo.setId("1");
        vo.setTitle("面试题集");
        when(interviewService.generate(eq(1L), any())).thenReturn(vo);

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"1\",\"targetRole\":\"Java\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("1"));
    }

    @Test
    void generateRateLimitedReturns429() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        doThrow(new BusinessException(ErrorCode.RATE_LIMITED))
                .when(rateLimiter).check(eq(1L), eq(AiAsyncService.TASK_INTERVIEW));

        mockMvc.perform(post("/api/ai/interview/generate")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":\"1\"}"))
                .andExpect(jsonPath("$.code").value(429));
        verifyNoInteractions(interviewService);
    }

    @Test
    void listReturnsSets() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        InterviewSetVO vo = new InterviewSetVO();
        vo.setId("1");
        when(interviewService.list(1L)).thenReturn(List.of(vo));

        mockMvc.perform(get("/api/ai/interview/sets").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("1"));
    }

    @Test
    void getDetailReturns200() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        InterviewSetVO vo = new InterviewSetVO();
        vo.setId("1");
        when(interviewService.get(1L, 1L)).thenReturn(vo);

        mockMvc.perform(get("/api/ai/interview/1").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void deleteReturns200() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        mockMvc.perform(delete("/api/ai/interview/1").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }
}
package com.resumegen.controller;

import com.resumegen.ai.PublicRateLimiter;
import com.resumegen.common.PageResult;
import com.resumegen.dto.PostDetailVO;
import com.resumegen.dto.PostVO;
import com.resumegen.service.CommunityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class CommunityControllerTest extends BaseMockMvcTest {

    @Mock
    private CommunityService communityService;
    @Mock
    private PublicRateLimiter rateLimiter;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new CommunityController(communityService, rateLimiter));
    }

    @Test
    void publicListAnonymousOk() throws Exception {
        when(communityService.listPublic(1L, 10L, null, null, null))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

        mockMvc.perform(get("/api/public/community/posts"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void publicDetailAnonymousOk() throws Exception {
        when(communityService.detailPublic(1L)).thenReturn(new PostDetailVO());

        mockMvc.perform(get("/api/public/community/posts/1"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void publicCommentsAnonymousOk() throws Exception {
        when(communityService.listComments(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/public/community/posts/1/comments"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void publishWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/community/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":1,\"title\":\"t\"}"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void likeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/community/posts/1/like"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void commentWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/community/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void myWithTokenOk() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        when(communityService.my(1L)).thenReturn(new com.resumegen.dto.MyCommunityVO());

        mockMvc.perform(get("/api/community/me").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }
}
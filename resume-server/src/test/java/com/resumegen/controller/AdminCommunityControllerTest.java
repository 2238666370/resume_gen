package com.resumegen.controller;

import com.resumegen.common.PageResult;
import com.resumegen.service.CommunityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AdminCommunityControllerTest extends BaseMockMvcTest {

    @Mock
    private CommunityService communityService;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new AdminCommunityController(communityService));
    }

    @Test
    void normalUserAuditReturns403() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(put("/admin/community/posts/1/audit")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":4}"))
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(communityService);
    }

    @Test
    void adminListOk() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        when(communityService.adminList(1L, 10L, null))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

        mockMvc.perform(get("/admin/community/posts").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void adminReportsOk() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        when(communityService.adminReports(1L, 10L))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

        mockMvc.perform(get("/admin/community/reports").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }
}
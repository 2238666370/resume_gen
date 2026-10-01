package com.resumegen.controller;

import com.resumegen.dto.AiKbEntryVO;
import com.resumegen.service.AiKbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AdminKbControllerTest extends BaseMockMvcTest {

    @Mock
    private AiKbService aiKbService;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new AdminKbController(aiKbService));
    }

    @Test
    void normalUserAccessReturns403() throws Exception {
        String token = tokenFor(1L, "USER", 0L);
        mockMvc.perform(get("/admin/ai/kb").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(aiKbService);
    }

    @Test
    void adminListReturns200() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        AiKbEntryVO vo = new AiKbEntryVO();
        vo.setId("1");
        vo.setKbType("interview_q");
        when(aiKbService.list(null)).thenReturn(List.of(vo));

        mockMvc.perform(get("/admin/ai/kb").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value("1"));
    }

    @Test
    void adminCreateReturns200() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        mockMvc.perform(post("/admin/ai/kb")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbType\":\"interview_q\",\"content\":\"内容\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void adminCreateMissingContentReturns400() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        mockMvc.perform(post("/admin/ai/kb")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbType\":\"interview_q\"}"))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(aiKbService);
    }

    @Test
    void adminDeleteReturns200() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        mockMvc.perform(delete("/admin/ai/kb/1").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }
}
package com.resumegen.controller;

import com.resumegen.common.PageResult;
import com.resumegen.dto.TemplateVO;
import com.resumegen.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AdminTemplateControllerTest extends BaseMockMvcTest {

    @Mock
    private TemplateService templateService;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new AdminTemplateController(templateService));
    }

    @Test
    void normalUserCreateReturns403() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(post("/admin/templates")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"x\",\"name\":\"y\"}"))
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(templateService);
    }

    @Test
    void adminListOk() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        when(templateService.adminList(1L, 10L))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

        mockMvc.perform(get("/admin/templates").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void adminCreateOk() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        TemplateVO vo = new TemplateVO();
        vo.setId("9");
        vo.setCode("new");
        vo.setStatus(1);
        when(templateService.create(any())).thenReturn(vo);

        mockMvc.perform(post("/admin/templates")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"new\",\"name\":\"新模板\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("new"));
    }
}
package com.resumegen.controller;

import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.TemplateVO;
import com.resumegen.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class TemplateControllerTest extends BaseMockMvcTest {

    @Mock
    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        buildMockMvc(new TemplateController(templateService));
    }

    private TemplateVO vo(String code) {
        TemplateVO v = new TemplateVO();
        v.setId("1");
        v.setCode(code);
        v.setName("模板");
        v.setType("official");
        v.setStatus(1);
        return v;
    }

    @Test
    void listWithoutTokenOk() throws Exception {
        when(templateService.listPublic()).thenReturn(List.of(vo("classic")));

        mockMvc.perform(get("/api/templates"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].code").value("classic"));
    }

    @Test
    void detailWithoutTokenOk() throws Exception {
        when(templateService.getByCode("modern")).thenReturn(vo("modern"));

        mockMvc.perform(get("/api/templates/detail/modern"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("modern"));
    }

    @Test
    void detailUnknownReturns404() throws Exception {
        when(templateService.getByCode("nope"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND.getCode(), "模板不存在"));

        mockMvc.perform(get("/api/templates/detail/nope"))
                .andExpect(jsonPath("$.code").value(404));
    }
}
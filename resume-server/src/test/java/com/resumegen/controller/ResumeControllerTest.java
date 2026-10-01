package com.resumegen.controller;

import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.common.PageResult;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.service.ResumeService;
import com.resumegen.service.ResumeSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ResumeControllerTest extends BaseMockMvcTest {

    @Mock
    private ResumeService resumeService;
    @Mock
    private ResumeSnapshotService snapshotService;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new ResumeController(resumeService, snapshotService));
    }

    private String bearer() {
        return "Bearer " + tokenFor(1L, "USER", 0L);
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/resumes"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void listCapsSizeToMax100() throws Exception {
        when(resumeService.list(1L, 1L, 100L, null))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 100));

        mockMvc.perform(get("/api/resumes").param("size", "200").header(AUTH, bearer()))
                .andExpect(jsonPath("$.code").value(0));

        verify(resumeService).list(1L, 1L, 100L, null);
    }

    @Test
    void crudFlow() throws Exception {
        ResumeDetailVO created = new ResumeDetailVO();
        created.setId("r1");
        created.setTitle("我的简历");
        created.setVersion(0);

        // 新建
        when(resumeService.create(eq(1L), anyString(), any())).thenReturn(created);
        mockMvc.perform(post("/api/resumes")
                        .header(AUTH, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"我的简历\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("r1"));

        // 查
        when(resumeService.get(1L, "r1")).thenReturn(created);
        mockMvc.perform(get("/api/resumes/r1").header(AUTH, bearer()))
                .andExpect(jsonPath("$.data.id").value("r1"));

        // 改（乐观锁 version=0）
        when(resumeService.update(eq(1L), eq("r1"), any(ResumeDTO.class), eq(0))).thenReturn(created);
        mockMvc.perform(put("/api/resumes/r1")
                        .param("version", "0")
                        .header(AUTH, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(0));

        // 删
        mockMvc.perform(delete("/api/resumes/r1").header(AUTH, bearer()))
                .andExpect(jsonPath("$.code").value(0));
        verify(resumeService).delete(1L, "r1");

        // 删后再查 → 404
        when(resumeService.get(1L, "r1")).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));
        mockMvc.perform(get("/api/resumes/r1").header(AUTH, bearer()))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void exportAndImport() throws Exception {
        ResumeDTO dto = new ResumeDTO();
        dto.setTitle("导出简历");
        when(resumeService.exportJson(1L, "r1")).thenReturn(dto);

        mockMvc.perform(get("/api/resumes/r1/export.json").header(AUTH, bearer()))
                .andExpect(jsonPath("$.title").value("导出简历"));

        ResumeDetailVO imported = new ResumeDetailVO();
        imported.setId("r9");
        imported.setTitle("导入简历");
        when(resumeService.importJson(eq(1L), any(ResumeDTO.class))).thenReturn(imported);

        mockMvc.perform(post("/api/resumes/import")
                        .header(AUTH, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("r9"));
    }

    @Test
    void updateWithInvalidAccentColorReturns400() throws Exception {
        mockMvc.perform(put("/api/resumes/r1")
                        .header(AUTH, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accentColor\":\"red\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateWithInvalidDateReturns400() throws Exception {
        mockMvc.perform(put("/api/resumes/r1")
                        .header(AUTH, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"education\":[{\"endDate\":\"2026/1\"}]}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateWithScriptInDescriptionReturns400() throws Exception {
        mockMvc.perform(put("/api/resumes/r1")
                        .header(AUTH, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":[{\"description\":\"<script>alert(1)</script>\"}]}"))
                .andExpect(jsonPath("$.code").value(400));
    }
}
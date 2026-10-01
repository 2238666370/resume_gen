package com.resumegen.controller;

import com.resumegen.common.PageResult;
import com.resumegen.dto.AdminResumeItemVO;
import com.resumegen.dto.UserVO;
import com.resumegen.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AdminControllerTest extends BaseMockMvcTest {

    @Mock
    private AdminService adminService;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new AdminController(adminService));
    }

    @Test
    void normalUserAccessAdminReturns403() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(get("/admin/users").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(adminService);
    }

    @Test
    void adminAccessUsersReturns200() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        when(adminService.listUsers(1L, 10L, null))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

        mockMvc.perform(get("/admin/users").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void adminAccessResumesContainsUsername() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        AdminResumeItemVO item = new AdminResumeItemVO();
        item.setId("1");
        item.setTitle("r");
        item.setUsername("alice");
        when(adminService.listAllResumes(1L, 10L, null))
                .thenReturn(new PageResult<>(List.of(item), 1, 1, 10));

        mockMvc.perform(get("/admin/resumes").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].username").value("alice"));
    }

    @Test
    void adminSetUserRoleReturns200() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);

        mockMvc.perform(put("/admin/users/2/role").header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void adminUserResumesReturns200() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        when(adminService.listUserResumes(2L, 1L, 10L))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 10));

        mockMvc.perform(get("/admin/users/2/resumes").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }
}
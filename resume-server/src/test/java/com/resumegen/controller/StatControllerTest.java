package com.resumegen.controller;

import com.resumegen.dto.PvUvVO;
import com.resumegen.dto.StatsOverviewVO;
import com.resumegen.dto.TrendPointVO;
import com.resumegen.service.StatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class StatControllerTest extends BaseMockMvcTest {

    @Mock
    private StatService statService;

    private static final String AUTH = "Authorization";

    @BeforeEach
    void setUp() {
        buildMockMvc(new StatController(statService));
    }

    @Test
    void trackPublicWithoutTokenSucceeds() throws Exception {
        mockMvc.perform(post("/api/public/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":\"/s/abc\",\"deviceId\":\"d1\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void trackAuthedUsesUserId() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(post("/api/stats/track")
                        .header(AUTH, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":\"/editor/1\"}"))
                .andExpect(jsonPath("$.code").value(0));

        verify(statService).track(any(), eq(1L));
    }

    @Test
    void trackAuthedWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/stats/track")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(statService);
    }

    @Test
    void overviewNonAdminReturns403() throws Exception {
        String token = tokenFor(1L, "USER", 0L);

        mockMvc.perform(get("/admin/stats/overview").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(statService);
    }

    @Test
    void overviewAdminReturnsStats() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setTodayPv(100L);
        when(statService.overview()).thenReturn(vo);

        mockMvc.perform(get("/admin/stats/overview").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.todayPv").value(100));
    }

    @Test
    void trendAdminReturnsSeries() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        TrendPointVO p = new TrendPointVO();
        p.setTime("2026-10-01 00:00:00");
        p.setPv(5);
        p.setUv(2);
        when(statService.trend(anyString(), any(), any())).thenReturn(List.of(p));

        mockMvc.perform(get("/admin/stats/trend").param("granularity", "minute")
                        .header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].pv").value(5));
    }

    @Test
    void pvuvAdminReturnsRange() throws Exception {
        String token = tokenFor(1L, "ADMIN", 0L);
        when(statService.pvuv(any(), any())).thenReturn(new PvUvVO(10L, 3L));

        mockMvc.perform(get("/admin/stats/pvuv").header(AUTH, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.uv").value(3));
    }
}
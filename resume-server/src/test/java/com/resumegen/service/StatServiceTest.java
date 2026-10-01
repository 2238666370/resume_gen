package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.config.StatsProperties;
import com.resumegen.dto.PvUvVO;
import com.resumegen.dto.StatsOverviewVO;
import com.resumegen.dto.TrackRequest;
import com.resumegen.entity.AccessLog;
import com.resumegen.mapper.AccessLogMapper;
import com.resumegen.mapper.ResumeMapper;
import com.resumegen.mapper.SysUserMapper;
import com.resumegen.mq.MessageQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatServiceTest {

    @Mock
    private AccessLogMapper accessLogMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private StatLogWriter statLogWriter;
    @Mock
    private MessageQueue messageQueue;

    private final ObjectMapper om = new ObjectMapper().findAndRegisterModules();
    private StatsProperties statsProps;
    private StatService statService;

    @BeforeEach
    void setUp() {
        statsProps = new StatsProperties();
        statService = new StatService(accessLogMapper, userMapper, resumeMapper,
                statLogWriter, messageQueue, om, statsProps);
    }

    @Test
    void trackAsyncPublishesToQueue() {
        statsProps.getAsync().setEnabled(true);
        TrackRequest req = new TrackRequest();
        req.setPage("/editor/1");
        req.setResumeId(1L);
        req.setDeviceId("dev1");

        statService.track(req, 2L);

        verify(messageQueue).publish(eq(StatLogConsumer.QUEUE), anyString());
        verify(statLogWriter, never()).write(any());
    }

    @Test
    void trackSyncWritesWhenAsyncDisabled() {
        statsProps.getAsync().setEnabled(false);
        TrackRequest req = new TrackRequest();
        req.setPage("/editor/1");

        statService.track(req, 2L);

        verify(statLogWriter).write(any(AccessLog.class));
        verify(messageQueue, never()).publish(anyString(), anyString());
    }

    @Test
    void trackAsyncFallsBackToSyncOnPublishFailure() {
        statsProps.getAsync().setEnabled(true);
        doThrow(new RuntimeException("queue down")).when(messageQueue).publish(anyString(), anyString());
        TrackRequest req = new TrackRequest();
        req.setPage("/editor/1");

        statService.track(req, 2L);

        verify(statLogWriter).write(any(AccessLog.class));
    }

    @Test
    void overviewAggregatesCounts() {
        when(userMapper.selectCount(null)).thenReturn(10L);
        when(resumeMapper.selectCount(null)).thenReturn(20L);
        when(accessLogMapper.countPv(any(), any())).thenReturn(150L);
        when(accessLogMapper.countUv(any(), any())).thenReturn(30L);

        StatsOverviewVO vo = statService.overview();

        assertThat(vo.getUserCount()).isEqualTo(10L);
        assertThat(vo.getResumeCount()).isEqualTo(20L);
        assertThat(vo.getTodayPv()).isEqualTo(150L);
        assertThat(vo.getTodayUv()).isEqualTo(30L);
        assertThat(vo.getOnlineUsers()).isEqualTo(30L);
    }

    @Test
    void pvuvSumsRange() {
        when(accessLogMapper.countPv(any(), any())).thenReturn(100L);
        when(accessLogMapper.countUv(any(), any())).thenReturn(25L);

        PvUvVO vo = statService.pvuv(LocalDateTime.now(), LocalDateTime.now().plusDays(1));

        assertThat(vo.getPv()).isEqualTo(100L);
        assertThat(vo.getUv()).isEqualTo(25L);
    }
}

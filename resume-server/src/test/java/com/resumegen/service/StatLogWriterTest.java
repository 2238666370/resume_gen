package com.resumegen.service;

import com.resumegen.entity.AccessLog;
import com.resumegen.mapper.AccessLogMapper;
import com.resumegen.mapper.StatMinuteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StatLogWriterTest {

    @Mock
    private AccessLogMapper accessLogMapper;
    @Mock
    private StatMinuteMapper statMinuteMapper;

    private StatLogWriter writer;

    @BeforeEach
    void setUp() {
        writer = new StatLogWriter(accessLogMapper, statMinuteMapper);
    }

    @Test
    void writeInsertsLogAndUpsertsPv() {
        AccessLog log = new AccessLog();
        log.setTs(LocalDateTime.of(2026, 10, 1, 10, 5, 30));
        log.setPage("/editor/1");

        writer.write(log);

        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogMapper).insert(cap.capture());
        assertThat(cap.getValue().getPage()).isEqualTo("/editor/1");
        // 分钟截断：10:05:30 → 10:05:00
        verify(statMinuteMapper).upsertPv(LocalDateTime.of(2026, 10, 1, 10, 5, 0));
    }
}

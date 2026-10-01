package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.config.StatsProperties;
import com.resumegen.entity.AccessLog;
import com.resumegen.mq.MessageQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StatLogConsumerTest {

    @Mock
    private MessageQueue queue;
    @Mock
    private StatLogWriter statLogWriter;

    private final ObjectMapper om = new ObjectMapper().findAndRegisterModules();
    private StatsProperties props;
    private StatLogConsumer consumer;

    @BeforeEach
    void setUp() {
        props = new StatsProperties();
        consumer = new StatLogConsumer(queue, statLogWriter, om, props);
    }

    @Test
    void startSubscribesWhenAsyncEnabled() {
        props.getAsync().setEnabled(true);
        consumer.start();
        verify(queue).subscribe(eq(StatLogConsumer.QUEUE), anyInt(), any());
    }

    @Test
    void startSkipsWhenAsyncDisabled() {
        props.getAsync().setEnabled(false);
        consumer.start();
        verify(queue, never()).subscribe(any(), anyInt(), any());
    }

    @Test
    void handleParsesAndWrites() throws Exception {
        AccessLog log = new AccessLog();
        log.setPage("/editor/1");
        log.setTs(LocalDateTime.now());
        consumer.handle(om.writeValueAsString(log));

        verify(statLogWriter).write(any(AccessLog.class));
    }

    @Test
    void handleSwallowsInvalidPayload() {
        consumer.handle("not-a-json");
        verify(statLogWriter, never()).write(any());
    }
}

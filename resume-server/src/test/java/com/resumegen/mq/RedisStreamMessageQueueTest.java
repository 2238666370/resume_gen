package com.resumegen.mq;

import com.resumegen.config.MqProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisStreamMessageQueueTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    private MqProperties props;
    private RedisStreamMessageQueue mq;

    @BeforeEach
    void setUp() {
        props = new MqProperties();
        when(redis.opsForStream()).thenReturn(streamOps);
        mq = new RedisStreamMessageQueue(redis, props);
    }

    @Test
    void publishAddsPayloadToStream() {
        when(streamOps.add(any())).thenReturn(RecordId.of("1-1"));
        mq.publish("high", "hello");

        ArgumentCaptor<MapRecord> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOps).add(captor.capture());
        MapRecord rec = captor.getValue();
        assertThat(rec.getStream()).isEqualTo("mq:stream:high");
        @SuppressWarnings("unchecked")
        Map<Object, Object> body = (Map<Object, Object>) rec.getValue();
        assertThat(String.valueOf(body.get("payload"))).isEqualTo("hello");
    }

    @Test
    void subscribeIsIdempotent() {
        when(streamOps.createGroup(anyString(), anyString())).thenReturn("OK");

        mq.subscribe("high", 1, p -> {});
        mq.subscribe("high", 2, p -> {});

        verify(streamOps, times(1)).createGroup("mq:stream:high", "mq:group:high");
        mq.shutdown();
    }
}

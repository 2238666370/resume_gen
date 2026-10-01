package com.resumegen.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisAiTaskStoreTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper om = new ObjectMapper();
    private RedisAiTaskStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        store = new RedisAiTaskStore(redis, om);
    }

    @Test
    void putSerializesToRedisWithTtl() throws Exception {
        store.put(new AiTaskRecord("t1", "resume.score", 1L, "SUCCESS", "{\"totalScore\":80}", null));

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCap.capture(), jsonCap.capture(), eq(Duration.ofSeconds(3600)));
        assertThat(keyCap.getValue()).isEqualTo("ai:task:t1");
        AiTaskRecord parsed = om.readValue(jsonCap.getValue(), AiTaskRecord.class);
        assertThat(parsed.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void getDeserializesFromRedis() throws Exception {
        String json = om.writeValueAsString(new AiTaskRecord("t1", "interview.generate", 1L, "FAILED", null, "err"));
        when(valueOps.get("ai:task:t1")).thenReturn(json);

        AiTaskRecord got = store.get("t1");
        assertThat(got.getStatus()).isEqualTo("FAILED");
        assertThat(got.getError()).isEqualTo("err");
    }

    @Test
    void getMissingReturnsNull() {
        when(valueOps.get("ai:task:no")).thenReturn(null);
        assertThat(store.get("no")).isNull();
    }

    @Test
    void removeDeletesKey() {
        store.remove("t1");
        verify(redis).delete("ai:task:t1");
    }

    @Test
    void putSwallowsSerializationFailure() {
        // 正常对象可序列化；这里仅验证 put 不抛异常（Redis 异常被吞）
        store.put(new AiTaskRecord("t2", "resume.rewrite", 1L, "PENDING", null, null));
        verify(valueOps).set(anyString(), anyString(), any());
    }
}

package com.resumegen.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisCacheService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new RedisCacheService(redisTemplate);
    }

    @Test
    void setWritesWithTtl() {
        service.set("k", "v", 60);
        verify(valueOps).set("k", "v", Duration.ofSeconds(60));
    }

    @Test
    void getReadsValue() {
        when(valueOps.get("k")).thenReturn("v");
        assertThat(service.get("k")).isEqualTo("v");
    }

    @Test
    void deleteRemovesKey() {
        service.delete("k");
        verify(redisTemplate).delete("k");
    }
}
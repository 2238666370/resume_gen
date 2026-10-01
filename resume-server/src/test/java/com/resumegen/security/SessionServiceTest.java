package com.resumegen.security;

import com.resumegen.cache.CacheService;
import com.resumegen.config.ResumeProperties;
import com.resumegen.entity.SysUser;
import com.resumegen.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private CacheService cacheService;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        ResumeProperties props = new ResumeProperties(); // prefix=resume:, ttl=3600
        sessionService = new SessionService(userMapper, cacheService, props);
    }

    @Test
    void currentVersionHitsCache() {
        when(cacheService.get("resume:login:ver:1")).thenReturn("42");

        assertThat(sessionService.currentVersion(1L)).isEqualTo(42L);
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void currentVersionFallsBackToDbAndCaches() {
        when(cacheService.get(any())).thenReturn(null);
        SysUser user = new SysUser();
        user.setTokenVersion(7L);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertThat(sessionService.currentVersion(1L)).isEqualTo(7L);
        verify(cacheService).set(eq("resume:login:ver:1"), eq("7"), anyLong());
    }

    @Test
    void currentVersionNullUserReturnsZero() {
        when(cacheService.get(any())).thenReturn(null);
        when(userMapper.selectById(1L)).thenReturn(null);

        assertThat(sessionService.currentVersion(1L)).isEqualTo(0L);
    }

    @Test
    void invalidateDeletesCache() {
        sessionService.invalidate(1L);
        verify(cacheService).delete("resume:login:ver:1");
    }
}
package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.cache.CacheService;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.ProfileRequest;
import com.resumegen.dto.ProfileVO;
import com.resumegen.dto.ResumeDTO;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private CacheService cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumeProperties props = new ResumeProperties();
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userMapper, cacheService, props, objectMapper);
    }

    @Test
    void getCacheHitSkipsDb() {
        when(cacheService.get(any())).thenReturn("{\"phone\":\"13800000000\"}");

        ProfileVO vo = profileService.get(1L);

        assertThat(vo.getPhone()).isEqualTo("13800000000");
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void getCacheMissHitsDbAndWritesCache() {
        when(cacheService.get(any())).thenReturn(null);
        when(userMapper.selectById(1L)).thenReturn(null);

        ProfileVO vo = profileService.get(1L);

        assertThat(vo.getPhone()).isNull();
        verify(cacheService).set(any(), any(), anyLong());
    }

    @Test
    void updateWritesProfileColumns() {
        ProfileRequest req = new ProfileRequest();
        req.setName("张三");
        req.setPhone("13800000000");
        req.setEmail("a@b.c");

        ProfileVO vo = profileService.update(1L, req);

        verify(userMapper).updateProfile(any(SysUser.class));
        verify(cacheService).delete(any());
        assertThat(vo.getName()).isEqualTo("张三");
        assertThat(vo.getEmail()).isEqualTo("a@b.c");
    }

    @Test
    void getPersonalMapsFromProfile() {
        when(cacheService.get(any())).thenReturn(null);
        SysUser u = new SysUser();
        u.setId(1L);
        u.setName("张三");
        u.setPhone("13800000000");
        u.setEmail("a@b.c");
        when(userMapper.selectById(1L)).thenReturn(u);

        ResumeDTO.Personal personal = profileService.getPersonal(1L);

        assertThat(personal.getName()).isEqualTo("张三");
        assertThat(personal.getPhone()).isEqualTo("13800000000");
        assertThat(personal.getEmail()).isEqualTo("a@b.c");
    }
}
package com.resumegen.service;

import com.resumegen.common.BusinessException;
import com.resumegen.entity.SysUser;
import com.resumegen.mapper.ResumeMapper;
import com.resumegen.mapper.SysUserMapper;
import com.resumegen.security.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AdminService adminService;

    @Test
    void disableUserKicksOutSession() {
        SysUser u = new SysUser();
        u.setId(5L);
        u.setStatus(1);
        when(userMapper.selectById(5L)).thenReturn(u);

        adminService.setUserStatus(5L, 0);

        verify(userMapper).updateById(any(SysUser.class));
        verify(userMapper).incrementTokenVersion(5L);
        verify(sessionService).invalidate(5L);
    }

    @Test
    void enableUserDoesNotKickOutSession() {
        SysUser u = new SysUser();
        u.setId(5L);
        u.setStatus(0);
        when(userMapper.selectById(5L)).thenReturn(u);

        adminService.setUserStatus(5L, 1);

        verify(userMapper).updateById(any(SysUser.class));
        verify(userMapper, never()).incrementTokenVersion(5L);
        verify(sessionService, never()).invalidate(5L);
    }

    @Test
    void setStatusOnMissingUserThrowsNotFound() {
        when(userMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> adminService.setUserStatus(5L, 0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void updateRoleOnExistingUser() {
        SysUser u = new SysUser();
        u.setId(5L);
        when(userMapper.selectById(5L)).thenReturn(u);

        adminService.updateUserRole(5L, "ADMIN");

        verify(userMapper).updateById(any(SysUser.class));
        assertThat(u.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void updateRoleOnMissingUserThrowsNotFound() {
        when(userMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> adminService.updateUserRole(5L, "ADMIN"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }
}
package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.common.PageResult;
import com.resumegen.dto.AdminResumeItemVO;
import com.resumegen.dto.UserVO;
import com.resumegen.entity.Resume;
import com.resumegen.entity.SysUser;
import com.resumegen.mapper.ResumeMapper;
import com.resumegen.mapper.SysUserMapper;
import com.resumegen.security.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台业务（跨用户），仅依赖 MySQL mapper。
 */
@Service
public class AdminService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final SessionService sessionService;

    public AdminService(SysUserMapper userMapper, ResumeMapper resumeMapper, SessionService sessionService) {
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.sessionService = sessionService;
    }

    public PageResult<UserVO> listUsers(long page, long size, String keyword) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword));
        }
        qw.orderByDesc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), qw);
        List<UserVO> records = p.getRecords().stream().map(AuthService::toVO).collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), page, size);
    }

    @Transactional
    public void setUserStatus(Long userId, Integer status) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        u.setStatus(status);
        userMapper.updateById(u);
        // 禁用即时踢下线：递增令牌版本并失效会话缓存
        if (status != null && status == 0) {
            userMapper.incrementTokenVersion(userId);
            sessionService.invalidate(userId);
        }
    }

    public PageResult<AdminResumeItemVO> listAllResumes(long page, long size, String keyword) {
        LambdaQueryWrapper<Resume> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Resume::getTitle, keyword);
        }
        qw.orderByDesc(Resume::getUpdatedAt);
        Page<Resume> p = resumeMapper.selectPage(new Page<>(page, size), qw);

        Map<Long, String> usernameMap = usernamesOf(p.getRecords());

        List<AdminResumeItemVO> records = new ArrayList<>();
        for (Resume r : p.getRecords()) {
            AdminResumeItemVO vo = new AdminResumeItemVO();
            vo.setId(String.valueOf(r.getId()));
            vo.setTitle(r.getTitle());
            vo.setTemplateId(r.getTemplateId());
            vo.setUpdatedAt(r.getUpdatedAt() == null ? null : FMT.format(r.getUpdatedAt()));
            vo.setUserId(String.valueOf(r.getUserId()));
            vo.setUsername(usernameMap.getOrDefault(r.getUserId(), ""));
            records.add(vo);
        }
        return new PageResult<>(records, p.getTotal(), page, size);
    }

    @Transactional
    public void deleteResume(String resumeId) {
        long id;
        try {
            id = Long.parseLong(resumeId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Resume r = resumeMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        resumeMapper.deleteById(id);
    }

    @Transactional
    public void updateUserRole(Long userId, String role) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        u.setRole(role);
        userMapper.updateById(u);
    }

    public PageResult<AdminResumeItemVO> listUserResumes(Long userId, long page, long size) {
        LambdaQueryWrapper<Resume> qw = new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getUpdatedAt);
        Page<Resume> p = resumeMapper.selectPage(new Page<>(page, size), qw);

        Map<Long, String> usernameMap = usernamesOf(p.getRecords());
        List<AdminResumeItemVO> records = new ArrayList<>();
        for (Resume r : p.getRecords()) {
            AdminResumeItemVO vo = new AdminResumeItemVO();
            vo.setId(String.valueOf(r.getId()));
            vo.setTitle(r.getTitle());
            vo.setTemplateId(r.getTemplateId());
            vo.setUpdatedAt(r.getUpdatedAt() == null ? null : FMT.format(r.getUpdatedAt()));
            vo.setUserId(String.valueOf(r.getUserId()));
            vo.setUsername(usernameMap.getOrDefault(r.getUserId(), ""));
            records.add(vo);
        }
        return new PageResult<>(records, p.getTotal(), page, size);
    }

    public Map<String, Object> stats() {
        long userCount = userMapper.selectCount(null);
        long resumeCount = resumeMapper.selectCount(null);
        java.time.LocalDateTime start = LocalDate.now().atStartOfDay();
        long todayResumes = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().ge(Resume::getCreatedAt, start));
        long todayUsers = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().ge(SysUser::getCreatedAt, start));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userCount", userCount);
        m.put("resumeCount", resumeCount);
        m.put("todayResumes", todayResumes);
        m.put("todayUsers", todayUsers);
        return m;
    }

    private Map<Long, String> usernamesOf(List<Resume> resumes) {
        List<Long> ids = resumes.stream().map(Resume::getUserId).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (a, b) -> a));
    }
}
package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.cache.CacheService;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.ProfileRequest;
import com.resumegen.dto.ProfileVO;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.entity.SysUser;
import com.resumegen.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户个人信息：读增改 + Redis 缓存（Cache-Aside）。
 * 个人信息直接存于 sys_user 表，不单独建表。
 */
@Service
public class ProfileService {

    private final SysUserMapper userMapper;
    private final CacheService cacheService;
    private final ResumeProperties props;
    private final ObjectMapper objectMapper;

    public ProfileService(SysUserMapper userMapper, CacheService cacheService,
                          ResumeProperties props, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.cacheService = cacheService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    private String profileKey(Long userId) {
        return props.getCache().getPrefix() + "profile:" + userId;
    }

    public ProfileVO get(Long userId) {
        String key = profileKey(userId);
        String cached = cacheService.get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ProfileVO.class);
            } catch (Exception ignored) {
                // 反序列化失败则回源
            }
        }
        SysUser u = userMapper.selectById(userId);
        ProfileVO vo = u == null ? new ProfileVO() : toVO(u);
        try {
            cacheService.set(key, objectMapper.writeValueAsString(vo), props.getCache().getTtlSeconds());
        } catch (Exception ignored) {
            // 缓存写入失败不影响主流程
        }
        return vo;
    }

    public ProfileVO update(Long userId, ProfileRequest req) {
        SysUser u = new SysUser();
        u.setId(userId);
        u.setName(req.getName());
        u.setTitle(req.getTitle());
        u.setPhone(req.getPhone());
        u.setEmail(req.getEmail());
        u.setLocation(req.getLocation());
        u.setWebsite(req.getWebsite());
        u.setAvatar(req.getAvatar());
        u.setSummary(req.getSummary());
        userMapper.updateProfile(u);
        cacheService.delete(profileKey(userId));
        return toVO(u);
    }

    /** 供创建简历时预填个人板块。 */
    public ResumeDTO.Personal getPersonal(Long userId) {
        ProfileVO vo = get(userId);
        ResumeDTO.Personal personal = new ResumeDTO.Personal();
        personal.setName(vo.getName());
        personal.setTitle(vo.getTitle());
        personal.setEmail(vo.getEmail());
        personal.setPhone(vo.getPhone());
        personal.setLocation(vo.getLocation());
        personal.setWebsite(vo.getWebsite());
        personal.setAvatar(vo.getAvatar());
        personal.setSummary(vo.getSummary());
        return personal;
    }

    private ProfileVO toVO(SysUser u) {
        ProfileVO vo = new ProfileVO();
        vo.setName(u.getName());
        vo.setTitle(u.getTitle());
        vo.setPhone(u.getPhone());
        vo.setEmail(u.getEmail());
        vo.setLocation(u.getLocation());
        vo.setWebsite(u.getWebsite());
        vo.setAvatar(u.getAvatar());
        vo.setSummary(u.getSummary());
        return vo;
    }
}
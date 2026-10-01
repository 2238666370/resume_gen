package com.resumegen.security;

import com.resumegen.cache.CacheService;
import com.resumegen.config.ResumeProperties;
import com.resumegen.entity.SysUser;
import com.resumegen.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

/**
 * 单会话版本管理：DB 的 token_version 为唯一权威源，Redis 仅加速。
 */
@Service
public class SessionService {

    private final SysUserMapper userMapper;
    private final CacheService cacheService;
    private final ResumeProperties props;

    public SessionService(SysUserMapper userMapper, CacheService cacheService, ResumeProperties props) {
        this.userMapper = userMapper;
        this.cacheService = cacheService;
        this.props = props;
    }

    private String key(Long userId) {
        return props.getCache().getPrefix() + "login:ver:" + userId;
    }

    /** 查询当前有效版本：缓存未命中回源 DB（none 模式恒回源 DB）。 */
    public long currentVersion(Long userId) {
        String cached = cacheService.get(key(userId));
        if (cached != null) {
            return Long.parseLong(cached);
        }
        SysUser user = userMapper.selectById(userId);
        long ver = user == null ? 0L : user.getTokenVersion();
        cacheService.set(key(userId), String.valueOf(ver), props.getCache().getTtlSeconds());
        return ver;
    }

    /** 缓存失效（登录/登出后调用，强制下次回源 DB）。 */
    public void invalidate(Long userId) {
        cacheService.delete(key(userId));
    }
}
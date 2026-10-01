package com.resumegen.ai;

import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.ResumeProperties;
import org.springframework.stereotype.Component;

/**
 * 公开接口限流（R10-O6）：对分享查看 / 社区信息流等匿名高频接口按 key（IP/deviceId）
 * 限流，复用 {@link RateLimitStore}（Redis 全局 / 内存单机），超限抛 429。
 */
@Component
public class PublicRateLimiter {

    private final RateLimitStore store;
    private final ResumeProperties props;

    public PublicRateLimiter(RateLimitStore store, ResumeProperties props) {
        this.store = store;
        this.props = props;
    }

    /** 限流检查：超限抛 {@link ErrorCode#RATE_LIMITED}（429）。 */
    public void check(String key) {
        ResumeProperties.PublicRateLimit c = props.getPublicRateLimit();
        if (!c.isEnabled() || key == null) {
            return;
        }
        if (!store.tryAcquire(key, c.getLimit(), c.getWindowSeconds())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }
}

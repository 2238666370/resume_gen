package com.resumegen.ai;

import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.AiProperties;
import org.springframework.stereotype.Component;

/**
 * 单用户频控门面：按「user_id + task_type」计数，超阈值抛 429。
 *
 * <p>通过 {@link RateLimitStore} 抽象在 Redis（全局）与本地内存（单机）之间切换，
 * 由 {@code resume.cache.type} 决定；{@code ai.rate-limit.enabled=false} 时不限流。
 * 阈值按任务类型区分：{@code per-type} 覆盖、否则用默认 {@code limit}。
 */
@Component
public class AiRateLimiter {

    private final RateLimitStore store;
    private final AiProperties props;

    public AiRateLimiter(RateLimitStore store, AiProperties props) {
        this.store = store;
        this.props = props;
    }

    /** 频控检查：超限抛 {@link ErrorCode#RATE_LIMITED}（429），否则放行。 */
    public void check(Long userId, String taskType) {
        if (!props.getRateLimit().isEnabled() || userId == null) {
            return;
        }
        int limit = resolveLimit(taskType);
        if (limit <= 0) {
            return;
        }
        String key = userId + ":" + taskType;
        if (!store.tryAcquire(key, limit, props.getRateLimit().getWindowSeconds())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }

    private int resolveLimit(String taskType) {
        Integer v = props.getRateLimit().getPerType().get(taskType);
        return v != null ? v : props.getRateLimit().getLimit();
    }
}
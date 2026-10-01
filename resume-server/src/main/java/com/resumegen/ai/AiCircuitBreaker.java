package com.resumegen.ai;

import com.resumegen.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 调用熔断器（简单状态机：CLOSED → OPEN → HALF_OPEN）。
 *
 * <p>连续失败达到 {@code ai.circuit-breaker.failure-threshold} 后打开熔断，
 * 短路 {@code ai.circuit-breaker.open-seconds} 秒直接拒绝，避免上游雪崩；
 * 到期转半开试探，试探成功恢复关闭、失败则重新打开。可开关（enabled=false 时恒放行）。
 */
@Component
public class AiCircuitBreaker {

    private static final int STATE_CLOSED = 0;
    private static final int STATE_OPEN = 1;
    private static final int STATE_HALF_OPEN = 2;

    private final AiProperties props;

    private final AtomicInteger state = new AtomicInteger(STATE_CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);

    public AiCircuitBreaker(AiProperties props) {
        this.props = props;
    }

    /** 是否放行本次请求；返回 false 表示熔断打开中，调用方应直接降级返回。 */
    public boolean allowRequest() {
        if (!props.getCircuitBreaker().isEnabled()) {
            return true;
        }
        int s = state.get();
        if (s == STATE_CLOSED || s == STATE_HALF_OPEN) {
            return true;
        }
        // OPEN：判断是否已过短路窗口，到期转半开试探
        long openMillis = props.getCircuitBreaker().getOpenSeconds() * 1000L;
        if (openedAt.get() + openMillis <= System.currentTimeMillis()) {
            state.compareAndSet(STATE_OPEN, STATE_HALF_OPEN);
            return true;
        }
        return false;
    }

    /** 调用成功：复位失败计数并关闭熔断。 */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(STATE_CLOSED);
    }

    /** 调用失败：累加失败计数，达到阈值打开熔断。 */
    public void recordFailure() {
        if (!props.getCircuitBreaker().isEnabled()) {
            return;
        }
        int threshold = Math.max(1, props.getCircuitBreaker().getFailureThreshold());
        if (consecutiveFailures.incrementAndGet() >= threshold) {
            state.set(STATE_OPEN);
            openedAt.set(System.currentTimeMillis());
        }
    }
}
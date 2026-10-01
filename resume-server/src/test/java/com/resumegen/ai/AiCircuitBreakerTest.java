package com.resumegen.ai;

import com.resumegen.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCircuitBreakerTest {

    @Test
    void allowsWhenClosed() {
        AiCircuitBreaker cb = new AiCircuitBreaker(new AiProperties());
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    void opensAfterThresholdFailures() {
        AiProperties props = new AiProperties();
        props.getCircuitBreaker().setFailureThreshold(2);
        AiCircuitBreaker cb = new AiCircuitBreaker(props);

        cb.recordFailure();
        assertThat(cb.allowRequest()).isTrue();
        cb.recordFailure();
        assertThat(cb.allowRequest()).isFalse();
    }

    @Test
    void disabledAlwaysAllows() {
        AiProperties props = new AiProperties();
        props.getCircuitBreaker().setEnabled(false);
        AiCircuitBreaker cb = new AiCircuitBreaker(props);

        for (int i = 0; i < 10; i++) {
            cb.recordFailure();
        }
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    void successResetsAndCloses() {
        AiProperties props = new AiProperties();
        props.getCircuitBreaker().setFailureThreshold(2);
        AiCircuitBreaker cb = new AiCircuitBreaker(props);

        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.allowRequest()).isFalse();

        cb.recordSuccess();
        assertThat(cb.allowRequest()).isTrue();
        cb.recordFailure();
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    void halfOpenAllowsProbeAfterWindow() {
        AiProperties props = new AiProperties();
        props.getCircuitBreaker().setFailureThreshold(1);
        props.getCircuitBreaker().setOpenSeconds(0);
        AiCircuitBreaker cb = new AiCircuitBreaker(props);

        cb.recordFailure();
        // 窗口为 0：立即转半开并放行试探
        assertThat(cb.allowRequest()).isTrue();
    }
}
package com.resumegen.ai;

import com.resumegen.common.BusinessException;
import com.resumegen.config.ResumeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicRateLimiterTest {

    @Mock
    private RateLimitStore store;

    private ResumeProperties props;
    private PublicRateLimiter limiter;

    @BeforeEach
    void setUp() {
        props = new ResumeProperties();
        limiter = new PublicRateLimiter(store, props);
    }

    @Test
    void disabledSkipsCheck() {
        props.getPublicRateLimit().setEnabled(false);
        limiter.check("ip:1.2.3.4");
        verify(store, never()).tryAcquire(anyString(), anyInt(), anyInt());
    }

    @Test
    void enabledAllowsWithinLimit() {
        props.getPublicRateLimit().setEnabled(true);
        props.getPublicRateLimit().setLimit(10);
        props.getPublicRateLimit().setWindowSeconds(60);
        when(store.tryAcquire("ip:1.2.3.4", 10, 60)).thenReturn(true);

        assertThatCode(() -> limiter.check("ip:1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    void exceededThrows429() {
        props.getPublicRateLimit().setEnabled(true);
        when(store.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> limiter.check("ip:1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(429);
    }
}

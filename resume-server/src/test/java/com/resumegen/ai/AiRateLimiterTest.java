package com.resumegen.ai;

import com.resumegen.common.BusinessException;
import com.resumegen.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRateLimiterTest {

    @Mock
    private RateLimitStore store;

    private AiProperties props;
    private AiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        limiter = new AiRateLimiter(store, props);
    }

    @Test
    void disabledSkips() {
        props.getRateLimit().setEnabled(false);
        limiter.check(1L, "resume.rewrite");
        verifyNoInteractions(store);
    }

    @Test
    void nullUserIdSkips() {
        props.getRateLimit().setEnabled(true);
        limiter.check(null, "resume.rewrite");
        verifyNoInteractions(store);
    }

    @Test
    void overLimitThrows429() {
        props.getRateLimit().setEnabled(true);
        when(store.tryAcquire("1:resume.rewrite", 20, 60)).thenReturn(false);

        assertThatThrownBy(() -> limiter.check(1L, "resume.rewrite"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(429);
    }

    @Test
    void perTypeOverrideResolvesLimit() {
        props.getRateLimit().setEnabled(true);
        props.getRateLimit().getPerType().put("interview.generate", 3);
        when(store.tryAcquire("1:interview.generate", 3, 60)).thenReturn(true);

        limiter.check(1L, "interview.generate");
        verify(store).tryAcquire("1:interview.generate", 3, 60);
    }
}
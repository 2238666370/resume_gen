package com.resumegen.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

    private final InMemoryRateLimitStore store = new InMemoryRateLimitStore();

    @Test
    void acquiresUpToLimitThenRejects() {
        assertThat(store.tryAcquire("u:1", 2, 60)).isTrue();
        assertThat(store.tryAcquire("u:1", 2, 60)).isTrue();
        assertThat(store.tryAcquire("u:1", 2, 60)).isFalse();
    }

    @Test
    void keysAreIndependent() {
        assertThat(store.tryAcquire("u:a", 1, 60)).isTrue();
        assertThat(store.tryAcquire("u:a", 1, 60)).isFalse();
        assertThat(store.tryAcquire("u:b", 1, 60)).isTrue();
    }
}
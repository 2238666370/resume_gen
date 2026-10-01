package com.resumegen.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoopCacheServiceTest {

    private final NoopCacheService service = new NoopCacheService();

    @Test
    void getAlwaysReturnsNull() {
        assertThat(service.get("any")).isNull();
    }

    @Test
    void setAndDeleteAreNoop() {
        service.set("k", "v", 60);
        service.delete("k");
        assertThat(service.get("k")).isNull();
    }
}
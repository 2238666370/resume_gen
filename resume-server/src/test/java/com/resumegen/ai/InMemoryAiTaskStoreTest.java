package com.resumegen.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAiTaskStoreTest {

    private InMemoryAiTaskStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryAiTaskStore();
    }

    @Test
    void putAndGetRoundTrip() {
        store.put(new AiTaskRecord("t1", "interview.generate", 1L, "PENDING", null, null));

        AiTaskRecord got = store.get("t1");
        assertThat(got.getTaskId()).isEqualTo("t1");
        assertThat(got.getStatus()).isEqualTo("PENDING");
        assertThat(got.getUserId()).isEqualTo(1L);
    }

    @Test
    void getMissingReturnsNull() {
        assertThat(store.get("no-such")).isNull();
    }

    @Test
    void removeDeletesTask() {
        store.put(new AiTaskRecord("t1", "resume.rewrite", 1L, "PENDING", null, null));
        store.remove("t1");
        assertThat(store.get("t1")).isNull();
    }
}

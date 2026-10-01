package com.resumegen.mq;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMessageQueueTest {

    @Test
    void publishAndSubscribeRoundTrip() throws Exception {
        InMemoryMessageQueue mq = new InMemoryMessageQueue();
        CountDownLatch latch = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        mq.subscribe("high", 1, payload -> {
            received.add(payload);
            latch.countDown();
        });
        mq.publish("high", "hello");

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).containsExactly("hello");
        mq.shutdown();
    }

    @Test
    void multipleConsumersProcessConcurrently() throws Exception {
        InMemoryMessageQueue mq = new InMemoryMessageQueue();
        int total = 10;
        CountDownLatch latch = new CountDownLatch(total);
        ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        mq.subscribe("low", 3, payload -> {
            received.add(payload);
            latch.countDown();
        });
        for (int i = 0; i < total; i++) {
            mq.publish("low", "m" + i);
        }

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(total);
        mq.shutdown();
    }

    @Test
    void duplicateSubscribeIsIdempotent() throws Exception {
        InMemoryMessageQueue mq = new InMemoryMessageQueue();
        CountDownLatch latch = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        mq.subscribe("q", 1, payload -> {
            received.add(payload);
            latch.countDown();
        });
        // 重复订阅不应导致重复消费
        mq.subscribe("q", 2, payload -> received.add(payload));
        mq.publish("q", "once");

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
        assertThat(received).hasSize(1);
        mq.shutdown();
    }
}
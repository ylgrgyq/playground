package com.github.ylgrgyq.resender;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ObjectQueueConsumerTest {
    private final TestingStorage storage = new TestingStorage();
    private final ObjectQueueConsumerBuilder<TestingPayload> builder = ObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
            .setStorage(storage)
            .setDeserializer(new TestingPayloadCodec());

    @Before
    public void setUp() {
        storage.clear();
    }

    @Test
    public void fetchWithManualCommit() throws Exception {
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();
        TestingPayload first = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        TestingPayload second = new TestingPayload(2, "second".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(first.createObjectWithId());
        storedPayload.add(second.createObjectWithId());

        storage.store(storedPayload);

        ObjectQueueConsumer<TestingPayload> consumer = builder.setAutoCommit(false).build();

        TestingPayload payload = consumer.fetch();
        assertThat(consumer.fetch()).isSameAs(payload).isEqualTo(first);
        consumer.commit();
        assertThat(consumer.fetch()).isEqualTo(second).isNotEqualTo(first);
        consumer.close();
    }

    @Test
    public void fetchWithAutoCommit() throws Exception {
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();
        TestingPayload first = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        TestingPayload second = new TestingPayload(2, "second".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(first.createObjectWithId());
        storedPayload.add(second.createObjectWithId());

        storage.store(storedPayload);


        ObjectQueueConsumer<TestingPayload> consumer = builder.setAutoCommit(true).build();

        assertThat(consumer.fetch())
                .isEqualTo(first);
        assertThat(consumer.fetch())
                .isEqualTo(second)
                .isNotEqualTo(first);
        consumer.close();
    }

    @Test
    public void timeoutOnFetchWithAutoCommit() throws Exception {
        ObjectQueueConsumer<TestingPayload> consumer = ObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(true)
                .build();

        assertThat(consumer.fetch(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    public void timeoutOnFetchWithManualCommit() throws Exception {
        ObjectQueueConsumer<TestingPayload> consumer = builder
                .setAutoCommit(false)
                .build();

        assertThat(consumer.fetch(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    public void blockFetchWithAutoCommit() throws Exception {
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();
        final TestingPayload first = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(first.createObjectWithId());

        ObjectQueueConsumer<TestingPayload> consumer = builder
                .setAutoCommit(true)
                .build();

        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<TestingPayload> f = CompletableFuture.supplyAsync(() -> {
            try {
                barrier.await();
                return consumer.fetch();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        barrier.await();
        storage.store(storedPayload);

        await().until(f::isDone);
        assertThat(f).isCompletedWithValue(first);

        consumer.close();
    }

    @Test
    public void blockFetchWithManualCommit() throws Exception {
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();
        final TestingPayload first = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(first.createObjectWithId());

        ObjectQueueConsumer<TestingPayload> consumer = builder
                .setAutoCommit(false)
                .build();

        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<TestingPayload> f = CompletableFuture.supplyAsync(() -> {
            try {
                barrier.await();
                return consumer.fetch();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        barrier.await();
        storage.store(storedPayload);

        await().until(f::isDone);
        assertThat(f).isCompletedWithValue(first);

        consumer.close();
    }
}
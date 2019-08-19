package com.github.ylgrgyq.resender;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ObjectQueueConsumerTest {

    @Test
    public void fetchWithManualCommit() throws Exception {
        final TestingStorage storage = new TestingStorage();
        final ArrayList<ElementWithId> storedPayload = new ArrayList<>();
        TestingPayload first = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        TestingPayload second = new TestingPayload(2, "second".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(first.createPayloweWithId());
        storedPayload.add(second.createPayloweWithId());

        storage.store(storedPayload);

        ObjectQueueConsumer<TestingPayload> consumer = ObjectQueueConsumerBuilder.newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(false)
                .build();

        TestingPayload payload = consumer.fetch();
        assertThat(consumer.fetch()).isSameAs(payload).isEqualTo(first);
        consumer.commit();
        assertThat(consumer.fetch()).isEqualTo(second).isNotEqualTo(first);
        consumer.close();
    }

    @Test
    public void fetchWithAutoCommit() throws Exception {
        final TestingStorage storage = new TestingStorage();
        final ArrayList<ElementWithId> storedPayload = new ArrayList<>();
        TestingPayload first = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        TestingPayload second = new TestingPayload(2, "second".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(first.createPayloweWithId());
        storedPayload.add(second.createPayloweWithId());

        storage.store(storedPayload);


        ObjectQueueConsumer<TestingPayload> consumer = ObjectQueueConsumerBuilder.newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(true)
                .build();

        assertThat(consumer.fetch())
                .isEqualTo(first);
        assertThat(consumer.fetch())
                .isEqualTo(second)
                .isNotEqualTo(first);
        consumer.close();
    }

    @Test
    public void timeoutOnFetchWithAutoCommit() throws Exception {
        final TestingStorage storage = new TestingStorage();

        ObjectQueueConsumer<TestingPayload> consumer = ObjectQueueConsumerBuilder.newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(true)
                .build();

        assertThat(consumer.fetch(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    public void timeoutOnFetchWithManualCommit() throws Exception {
        final TestingStorage storage = new TestingStorage();

        ObjectQueueConsumer<TestingPayload> consumer = ObjectQueueConsumerBuilder.newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(false)
                .build();

        assertThat(consumer.fetch(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    public void blockFetchWithAutoCommit() throws Exception {
        final TestingStorage storage = new TestingStorage();
        final ArrayList<ElementWithId> storedPayload = new ArrayList<>();
        final TestingPayload first = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(first.createPayloweWithId());

        ObjectQueueConsumer<TestingPayload> consumer = ObjectQueueConsumerBuilder.newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
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
        final TestingStorage storage = new TestingStorage();
        final ArrayList<ElementWithId> storedPayload = new ArrayList<>();
        final TestingPayload first = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(first.createPayloweWithId());

        ObjectQueueConsumer<TestingPayload> consumer = ObjectQueueConsumerBuilder.newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
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
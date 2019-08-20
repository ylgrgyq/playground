package com.github.ylgrgyq.resender;

import com.spotify.futures.CompletableFutures;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

public class ObjectQueueProducerTest {
    private final TestingStorage storage = new TestingStorage();
    private final ObjectQueueProducerBuilder<TestingPayload> builder = ObjectQueueProducerBuilder.<TestingPayload>newBuilder()
            .setStorage(storage)
            .setSerializer(TestingPayload::getContent);

    @Before
    public void setUp() {
        storage.clear();
    }

    @Test
    public void simpleProduceAndFlush() {
        final ObjectQueueProducer<TestingPayload> producer = builder.build();

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            TestingPayload payload = new TestingPayload(("" + i).getBytes(StandardCharsets.UTF_8));
            CompletableFuture<Void> f = producer.produce(payload);
            assertThat(f).isNotNull();
            futures.add(f);
        }

        CompletableFuture<Void> flushFuture = producer.flush();
        await().until(flushFuture::isDone);

        assertThat(futures).allSatisfy(CompletableFuture::isDone);

        List<ObjectWithId> payloads = storage.getProdcedPayloads();
        for (int i = 0; i < payloads.size(); i++) {
            assertThat(payloads.get(i).getId()).isEqualTo(i + 1);
            assertThat(new String(payloads.get(i).getObjectInBytes(), StandardCharsets.UTF_8)).isEqualTo("" + i);
        }
    }

    @Test
    public void simpleProduceAndAutoFlush() {
        final ObjectQueueProducer<TestingPayload> producer = builder.build();

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            TestingPayload payload = new TestingPayload(("" + i).getBytes(StandardCharsets.UTF_8));
            CompletableFuture<Void> f = producer.produce(payload);
            assertThat(f).isNotNull();
            futures.add(f);
        }

        await().until(() -> CompletableFutures.allAsList(futures).isDone());
        assertThat(futures).allSatisfy(CompletableFuture::isDone);

        List<ObjectWithId> payloads = storage.getProdcedPayloads();
        for (int i = 0; i < payloads.size(); i++) {
            assertThat(payloads.get(i).getId()).isEqualTo(i + 1);
            assertThat(new String(payloads.get(i).getObjectInBytes(), StandardCharsets.UTF_8)).isEqualTo("" + i);
        }
    }

    @Test
    public void close() throws Exception {
        final ObjectQueueProducer<TestingPayload> producer = builder.build();

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            TestingPayload payload = new TestingPayload(("" + i).getBytes(StandardCharsets.UTF_8));
            CompletableFuture<Void> f = producer.produce(payload);
            assertThat(f).isNotNull();
            futures.add(f);
        }

        producer.close();

        assertThat(futures).allSatisfy(CompletableFuture::isDone);
        assertThat(storage.closed()).isTrue();
    }

    @Test
    public void produceWhenProducerStopped() throws Exception {
        final ObjectQueueProducer<TestingPayload> producer = builder.build();

        producer.close();

        assertThatThrownBy(() -> producer.produce(new TestingPayload()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("producer has been closed");
    }

    @Test
    public void produceWhenSerializeElementFailed() {
        final ObjectQueueProducer<TestingPayload> producer = builder
                .setSerializer(bs -> {
                    throw new SerializationException();
                }).build();

        assertThatThrownBy(() -> producer.produce(new TestingPayload()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(SerializationException.class);
    }
}
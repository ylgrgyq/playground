package com.github.ylgrgyq.reservoir;

import com.spotify.futures.CompletableFutures;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

public class DisruptorBackedObjectQueueProducerTest {
    private final TestingStorage storage = new TestingStorage();
    private final ObjectQueueProducerBuilder<TestingPayload> builder = ObjectQueueProducerBuilder.<TestingPayload>newBuilder()
            .setStorage(storage)
            .setSerializer(TestingPayload::getContent);

    @Before
    public void setUp() {
        storage.clear();
    }

    @Test
    public void simpleProduceAndFlush() throws Exception {
        final DisruptorBackedObjectQueueProducer<TestingPayload> producer = builder.build();

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
    public void simpleProduceAndAutoFlush() throws Exception {
        final DisruptorBackedObjectQueueProducer<TestingPayload> producer = builder.build();

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
    public void flushAllProducedObjectOnClose() throws Exception {
        final DisruptorBackedObjectQueueProducer<TestingPayload> producer = builder
                .setBatchSize(5)
                .setStorage(new AbstractTestingStorage() {
                    @Override
                    public void store(Collection<ObjectWithId> batch) throws StorageException {
                        try {
                            Thread.sleep(200);
                        } catch (Exception ex) {
                            throw new StorageException(ex);
                        }
                    }
                }).build();

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            TestingPayload payload = new TestingPayload(("" + i).getBytes(StandardCharsets.UTF_8));
            CompletableFuture<Void> f = producer.produce(payload);
            assertThat(f).isNotNull();
            futures.add(f);
        }

        producer.close();
        assertThat(futures).allSatisfy(CompletableFuture::isDone);
    }

    @Test
    public void produceAfterClose() throws Exception {
        final DisruptorBackedObjectQueueProducer<TestingPayload> producer = builder.build();

        producer.close();

        assertThatThrownBy(() -> producer.produce(new TestingPayload()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("producer has been closed");
    }

    @Test
    public void produceWhenSerializeElementFailed() throws Exception {
        final DisruptorBackedObjectQueueProducer<TestingPayload> producer = builder
                .setSerializer(bs -> {
                    throw new SerializationException();
                }).build();

        assertThatThrownBy(() -> producer.produce(new TestingPayload()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(SerializationException.class);
    }

    @Test
    public void storageThrowsStorageException() throws Exception {
        DisruptorBackedObjectQueueProducer<TestingPayload> producer = builder.setStorage(new AbstractTestingStorage() {
            @Override
            public void store(Collection<ObjectWithId> batch) throws StorageException {
                throw new StorageException("deliberate store failed");
            }
        }).build();

        TestingPayload payload = new TestingPayload(("Hello").getBytes(StandardCharsets.UTF_8));
        CompletableFuture<Void> f = producer.produce(payload);
        await().until(f::isDone);
        assertThat(f).hasFailedWithThrowableThat().hasMessageContaining("store failed").isInstanceOf(StorageException.class);
    }

    @Test
    public void storageThrowsOtherException() throws Exception {
        DisruptorBackedObjectQueueProducer<TestingPayload> producer = builder.setStorage(new AbstractTestingStorage() {
            @Override
            public void store(Collection<ObjectWithId> batch) {
                throw new RuntimeException("deliberate store failed");
            }
        }).build();

        TestingPayload payload = new TestingPayload(("Hello").getBytes(StandardCharsets.UTF_8));
        CompletableFuture<Void> f = producer.produce(payload);
        await().until(f::isDone);
        assertThat(f).hasFailedWithThrowableThat().hasMessageContaining("store failed").isInstanceOf(RuntimeException.class);
    }
}
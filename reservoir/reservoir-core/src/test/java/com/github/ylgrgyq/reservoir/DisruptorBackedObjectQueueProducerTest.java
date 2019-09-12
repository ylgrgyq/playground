package com.github.ylgrgyq.reservoir;

import com.spotify.futures.CompletableFutures;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static com.github.ylgrgyq.reservoir.TestingUtils.numberStringBytes;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

public class DisruptorBackedObjectQueueProducerTest {
    private final TestingStorage<byte[]> storage = new TestingStorage<>();
    private final TestingPayloadCodec codec = new TestingPayloadCodec();
    private final ObjectQueueBuilder<TestingPayload, byte[]> builder = ObjectQueueBuilder.newBuilder(storage, codec);

    @Before
    public void setUp() {
        storage.clear();
    }

    @Test
    public void simpleProduceAndFlush() throws Exception {
        final ObjectQueueProducer<TestingPayload> producer = builder.buildProducer();

        final List<TestingPayload> producedPayload = new ArrayList<>();
        final ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            final TestingPayload payload = new TestingPayload(numberStringBytes(i));
            final CompletableFuture<Void> f = producer.produce(payload);
            assertThat(f).isNotNull();
            producedPayload.add(payload);
            futures.add(f);
        }

        CompletableFuture<Void> flushFuture = producer.flush();
        await().until(flushFuture::isDone);

        assertThat(futures).allSatisfy(CompletableFuture::isDone);

        final List<ObjectWithId<byte[]>> payloads = storage.getProdcedPayloads();
        assertThat(producedPayload).isEqualTo(
                payloads.stream().map(payload -> {
                            try {
                                return codec.deserialize(payload.getSerializedObject());
                            } catch (DeserializationException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                ).collect(Collectors.toList()));
    }

    @Test
    public void simpleProduceAndAutoFlush() throws Exception {
        final ObjectQueueProducer<TestingPayload> producer = builder.buildProducer();
        final List<TestingPayload> producedPayloads = new ArrayList<>();
        final ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            final TestingPayload payload = new TestingPayload(numberStringBytes(i));
            CompletableFuture<Void> f = producer.produce(payload);
            assertThat(f).isNotNull();
            futures.add(f);
            producedPayloads.add(payload);
        }

        await().until(() -> CompletableFutures.allAsList(futures).isDone());
        assertThat(futures).allSatisfy(CompletableFuture::isDone);

        final List<ObjectWithId<byte[]>> payloads = storage.getProdcedPayloads();
        assertThat(producedPayloads).isEqualTo(
                payloads.stream().map(payload -> {
                            try {
                                return codec.deserialize(payload.getSerializedObject());
                            } catch (DeserializationException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                ).collect(Collectors.toList()));
    }

    @Test
    public void flushAllProducedObjectOnClose() throws Exception {
        final ObjectQueueProducer<TestingPayload> producer = builder
                .setConsumerFetchBatchSize(5)
                .replaceStorage(new AbstractTestingStorage<byte[]>() {
                    @Override
                    public void store(List<byte[]> batch) throws StorageException {
                        try {
                            Thread.sleep(200);
                        } catch (Exception ex) {
                            throw new StorageException(ex);
                        }
                    }
                }).buildProducer();

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final TestingPayload payload = new TestingPayload(numberStringBytes(i));
            CompletableFuture<Void> f = producer.produce(payload);
            assertThat(f).isNotNull();
            futures.add(f);
        }

        producer.close();
        assertThat(futures).allSatisfy(CompletableFuture::isDone);
    }

    @Test
    public void produceAfterClose() throws Exception {
        final ObjectQueueProducer<TestingPayload> producer = builder.buildProducer();

        producer.close();

        assertThatThrownBy(() -> producer.produce(new TestingPayload()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("producer has been closed");
    }

    @Test
    public void produceWhenSerializeElementFailed() throws Exception {
        final ObjectQueueProducer<TestingPayload> producer = builder
                .replaceCodec(new BadTestingPayloadCodec<>())
                .buildProducer();

        assertThatThrownBy(() -> producer.produce(new TestingPayload()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(SerializationException.class);
    }

    @Test
    public void storageThrowsStorageException() throws Exception {
        ObjectQueueProducer<TestingPayload> producer = builder.replaceStorage(new AbstractTestingStorage<byte[]>() {
            @Override
            public void store(List<byte[]> batch) throws StorageException {
                throw new StorageException("deliberate store failed");
            }
        }).buildProducer();

        TestingPayload payload = new TestingPayload(("Hello").getBytes(StandardCharsets.UTF_8));
        CompletableFuture<Void> f = producer.produce(payload);
        await().until(f::isDone);
        assertThat(f).hasFailedWithThrowableThat().hasMessageContaining("store failed").isInstanceOf(StorageException.class);
    }

    @Test
    public void storageThrowsOtherException() throws Exception {
        ObjectQueueProducer<TestingPayload> producer = builder.replaceStorage(new AbstractTestingStorage<byte[]>() {
            @Override
            public void store(List<byte[]> batch) {
                throw new RuntimeException("deliberate store failed");
            }
        }).buildProducer();

        TestingPayload payload = new TestingPayload(("Hello").getBytes(StandardCharsets.UTF_8));
        CompletableFuture<Void> f = producer.produce(payload);
        await().until(f::isDone);
        assertThat(f).hasFailedWithThrowableThat().hasMessageContaining("store failed").isInstanceOf(RuntimeException.class);
    }
}
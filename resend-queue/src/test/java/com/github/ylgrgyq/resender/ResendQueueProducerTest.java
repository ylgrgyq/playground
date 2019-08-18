package com.github.ylgrgyq.resender;

import com.spotify.futures.CompletableFutures;
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

public class ResendQueueProducerTest {

    @Test
    public void simpleProduceAndFlush() {
        final TestingProducerStorage storage = new TestingProducerStorage();
        final ResendQueueProducer<TestingPayload> producer = new ResendQueueProducer<TestingPayload>(storage, TestingPayload::getContent);

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

        List<PayloadWithId> payloads = storage.getProdcedPayloads();
        for (int i = 0; i < payloads.size(); i++) {
            assertThat(payloads.get(i).getId()).isEqualTo(i + 1);
            assertThat(new String(payloads.get(i).getPayload(), StandardCharsets.UTF_8)).isEqualTo("" + i);
        }
    }

    @Test
    public void simpleProduceAndAutoFlush() {
        final TestingProducerStorage storage = new TestingProducerStorage();
        final ResendQueueProducer<TestingPayload> producer = new ResendQueueProducer<TestingPayload>(storage, TestingPayload::getContent);

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            TestingPayload payload = new TestingPayload(("" + i).getBytes(StandardCharsets.UTF_8));
            CompletableFuture<Void> f = producer.produce(payload);
            assertThat(f).isNotNull();
            futures.add(f);
        }

        await().until(() -> CompletableFutures.allAsList(futures).isDone());
        assertThat(futures).allSatisfy(CompletableFuture::isDone);

        List<PayloadWithId> payloads = storage.getProdcedPayloads();
        for (int i = 0; i < payloads.size(); i++) {
            assertThat(payloads.get(i).getId()).isEqualTo(i + 1);
            assertThat(new String(payloads.get(i).getPayload(), StandardCharsets.UTF_8)).isEqualTo("" + i);
        }
    }

    @Test
    public void close() throws Exception {
        final TestingProducerStorage storage = new TestingProducerStorage();
        final ResendQueueProducer<TestingPayload> producer = new ResendQueueProducer<TestingPayload>(storage, TestingPayload::getContent);

        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            TestingPayload payload = new TestingPayload(("" + i).getBytes(StandardCharsets.UTF_8));
            CompletableFuture<Void> f = producer.produce(payload);
            assertThat(f).isNotNull();
            futures.add(f);
        }

        producer.close();

        assertThat(futures).allSatisfy(CompletableFuture::isDone);
        assertThat(storage.isStopped()).isTrue();
    }

    @Test
    public void produceWhenProducerStopped() throws Exception {
        final TestingProducerStorage storage = new TestingProducerStorage();
        final ResendQueueProducer<TestingPayload> producer = new ResendQueueProducer<TestingPayload>(storage, TestingPayload::getContent);

        producer.close();

        assertThatThrownBy(() -> producer.produce(new TestingPayload()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("producer has been stopped");
    }

    @Test
    public void produceWhenSerializeElementFailed() {
        final TestingProducerStorage storage = new TestingProducerStorage();
        final ResendQueueProducer<TestingPayload> producer = new ResendQueueProducer<TestingPayload>(storage, bs -> {
            throw new SerializationException();
        });

        assertThatThrownBy(() -> producer.produce(new TestingPayload()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(SerializationException.class);
    }

    private static class TestingPayload implements Payload {
        private byte[] content;

        public TestingPayload() {
            this.content = ("Hello").getBytes(StandardCharsets.UTF_8);
        }

        public TestingPayload(byte[] content) {
            this.content = content;
        }

        public byte[] getContent() {
            return content;
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }

    private static class TestingProducerStorage implements ProducerStorage {
        private final List<PayloadWithId> producedPayloads;
        private long lastId;
        private boolean stopped;

        public TestingProducerStorage() {
            this.producedPayloads = new ArrayList<>();
            this.lastId = 0;
        }

        List<PayloadWithId> getProdcedPayloads() {
            return producedPayloads;
        }

        @Override
        public long getLastProducedId() {
            return lastId;
        }

        @Override
        public void store(Collection<PayloadWithId> batch) {
            for (PayloadWithId payloadWithId : batch) {
                producedPayloads.add(payloadWithId);
                lastId = payloadWithId.getId();
            }
        }

        @Override
        public void close() throws Exception {
            stopped = true;
        }

        public boolean isStopped() {
            return stopped;
        }
    }
}
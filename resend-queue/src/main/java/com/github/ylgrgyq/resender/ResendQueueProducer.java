package com.github.ylgrgyq.resender;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class ResendQueueProducer<E extends Payload> implements AutoCloseable {
    private final BackupStorage<PayloadWithId> storage;
    private final Disruptor<ProducerEvent> disruptor;
    private final RingBuffer<ProducerEvent> ringBuffer;
    private final EventTranslatorThreeArg<ProducerEvent, byte[], CompletableFuture<Void>, Boolean> translator;
    private final Executor executor;

    public ResendQueueProducer(BackupStorage<PayloadWithId> storage) {
        this.storage = storage;
        final long lastId = storage.getLastId();
        this.disruptor = new Disruptor<>(ProducerEvent::new, 512,
                new NamedThreadFactory("Producer-Worker-"));
        this.disruptor.handleEventsWith(new ProduceHandler(512));
        this.disruptor.start();
        this.translator = new ProducerTranslator(lastId);
        this.ringBuffer = disruptor.getRingBuffer();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public CompletableFuture<Void> produce(E element) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            byte[] payload = element.serialize();
            ringBuffer.publishEvent(translator, payload, future, Boolean.FALSE);
        } catch (Exception ex) {
            future.completeExceptionally(ex);
        }

        return future;
    }

    public void flush() {
        ringBuffer.publishEvent(translator, null, new CompletableFuture<>(), Boolean.TRUE);
    }

    @Override
    public void close() throws Exception {
        disruptor.shutdown();
        storage.shutdown();
    }

    private final static class ProducerTranslator
            implements EventTranslatorThreeArg<ProducerEvent, byte[], CompletableFuture<Void>, Boolean> {
        private final AtomicLong nextId;

        ProducerTranslator(long nextId) {
            this.nextId = new AtomicLong(nextId);
        }

        @Override
        public void translateTo(ProducerEvent event, long sequence, byte[] payload, CompletableFuture<Void> future, Boolean flush) {
            event.reset();
            event.payloadWithId = new PayloadWithId(nextId.incrementAndGet(), payload);
            event.future = future;
            if (flush == Boolean.TRUE) {
                event.flush = true;
            }
        }
    }

    private final static class ProducerEvent {
        private PayloadWithId payloadWithId;
        private CompletableFuture<Void> future;
        private boolean flush;

        void reset() {
            payloadWithId = null;
            future = null;
            flush = false;
        }
    }

    private final class ProduceHandler implements EventHandler<ProducerEvent> {
        private final int batchSize;
        private final List<PayloadWithId> batchPayload;
        private final List<CompletableFuture<Void>> batchFutures;

        ProduceHandler(int batchSize) {
            this.batchSize = batchSize;
            this.batchPayload = new ArrayList<>(batchSize);
            this.batchFutures = new ArrayList<>(batchSize);
        }

        @Override
        public void onEvent(ProducerEvent event, long sequence, boolean endOfBatch) throws Exception {
            if (event.flush) {
                if (!batchPayload.isEmpty()) {
                    flush();
                }
            } else {
                batchPayload.add(event.payloadWithId);
                batchFutures.add(event.future);
                if (batchPayload.size() >= batchSize || endOfBatch) {
                    flush();
                }
            }

            assert batchPayload.size() == batchFutures.size() :
                    "batchPayload: " + batchPayload.size() + " batchFutures: " + batchFutures.size();
        }

        private void flush() {
            storage.store(batchPayload);

            batchPayload.clear();

            for (final CompletableFuture<Void> future : batchFutures) {
                executor.execute(() -> future.complete(null));
            }

            batchFutures.clear();
        }
    }
}

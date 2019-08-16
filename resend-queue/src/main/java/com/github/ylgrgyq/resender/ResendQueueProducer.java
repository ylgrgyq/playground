package com.github.ylgrgyq.resender;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.spotify.futures.CompletableFutures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class ResendQueueProducer<E extends PayloadCarrier> implements AutoCloseable {
    private final BackupStorage<PayloadCarrier<E>> storage;
    private final Disruptor<ProducerEvent<E>> disruptor;
    private final RingBuffer<ProducerEvent<E>> ringBuffer;
    private final EventTranslatorThreeArg<ProducerEvent<E>, E, CompletableFuture<Void>, Boolean> translator;
    private final Executor executor;

    public ResendQueueProducer(BackupStorage<PayloadCarrier<E>> storage) {
        this.storage = storage;
        final long lastId = storage.getLastId();
        this.disruptor = new Disruptor<>(ProducerEvent<E>::new, 512,
                new NamedThreadFactory("Producer-Worker-"));
        this.disruptor.handleEventsWith(new ProduceHandler(512));
        this.disruptor.start();
        this.translator = new ProducerTranslator<>(lastId);
        this.ringBuffer = disruptor.getRingBuffer();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public CompletableFuture<Void> produce(E element) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ringBuffer.publishEvent(translator, element, future, Boolean.FALSE);
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

    private final static class ProducerTranslator<T>
            implements EventTranslatorThreeArg<ProducerEvent<T>, T, CompletableFuture<Void>, Boolean> {
        private final AtomicLong nextId;

        ProducerTranslator(long nextId) {
            this.nextId = new AtomicLong(nextId);
        }

        @Override
        public void translateTo(ProducerEvent<T> event, long sequence, T payload, CompletableFuture<Void> future, Boolean flush) {
            event.reset();
            event.carrier = new PayloadCarrier<>(nextId.incrementAndGet(), payload);
            event.future = future;
            if (flush == Boolean.TRUE) {
                event.flush = true;
            }
        }
    }

    private final static class ProducerEvent<T> {
        private PayloadCarrier<T> carrier;
        private CompletableFuture<Void> future;
        private boolean flush;

        void reset() {
            carrier = null;
            future = null;
            flush = false;
        }
    }

    private final class ProduceHandler implements EventHandler<ProducerEvent<E>> {
        private final int batchSize;
        private final List<PayloadCarrier<E>> batchPayload;
        private final List<CompletableFuture<Void>> batchFutures;

        ProduceHandler(int batchSize) {
            this.batchSize = batchSize;
            this.batchPayload = new ArrayList<>(batchSize);
            this.batchFutures = new ArrayList<>(batchSize);
        }

        @Override
        public void onEvent(ProducerEvent<E> event, long sequence, boolean endOfBatch) throws Exception {
            if (event.flush) {
                if (!batchPayload.isEmpty()) {
                    flush();
                }
            } else {
                batchPayload.add(event.carrier);
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

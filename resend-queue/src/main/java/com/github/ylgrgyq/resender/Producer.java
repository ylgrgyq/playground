package com.github.ylgrgyq.resender;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class Producer<E extends PayloadCarrier> implements AutoCloseable {
    private final BackupStorage<PayloadCarrier<E>> storage;
    private final Disruptor<ProducerEvent<E>> disruptor;
    private final RingBuffer<ProducerEvent<E>> ringBuffer;
    private final EventTranslatorTwoArg<ProducerEvent<E>, E, Boolean> translator;
    private final Executor executor;

    public Producer(BackupStorage<PayloadCarrier<E>> storage) {
        this.storage = storage;
        final long lastId = storage.getLastId();
        this.disruptor = new Disruptor<>(ProducerEvent<E>::new, 512, new NamedThreadFactory("asdfasdf"));
        this.disruptor.handleEventsWith(new ProduceHandler(512));
        this.disruptor.start();
        this.translator = new ProducerTranslator<>(lastId);
        this.ringBuffer = disruptor.getRingBuffer();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public CompletableFuture<Void> produce(E element) {
        ringBuffer.publishEvent(translator, element, Boolean.FALSE);
        return null;
    }

    public void flush() {
        ringBuffer.publishEvent(translator, null, Boolean.TRUE);
    }

    @Override
    public void close() throws Exception {
        disruptor.shutdown();
        storage.shutdown();
    }

    private final static class ProducerTranslator<T> implements EventTranslatorTwoArg<ProducerEvent<T>, T, Boolean> {
        private final AtomicLong nextId;

        ProducerTranslator(long nextId) {
            this.nextId = new AtomicLong(nextId);
        }

        @Override
        public void translateTo(ProducerEvent<T> event, long sequence, T payload, Boolean flush) {
            event.reset();
            event.carrier = new PayloadCarrier<>(nextId.incrementAndGet(), payload);
            event.future = new CompletableFuture<>();
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

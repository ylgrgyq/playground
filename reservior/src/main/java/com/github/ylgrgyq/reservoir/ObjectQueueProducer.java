package com.github.ylgrgyq.reservoir;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static com.spotify.futures.CompletableFutures.exceptionallyCompletedFuture;
import static java.util.Objects.requireNonNull;

public final class ObjectQueueProducer<E> implements AutoCloseable {
    private final ProducerStorage storage;
    private final Disruptor<ProducerEvent> disruptor;
    private final RingBuffer<ProducerEvent> ringBuffer;
    private final EventTranslatorThreeArg<ProducerEvent, byte[], CompletableFuture<Void>, Boolean> translator;
    private final Executor executor;
    private final Serializer<E> serializer;
    private volatile boolean closed;

    public ObjectQueueProducer( ObjectQueueProducerBuilder<E> builder) {
        requireNonNull(builder, "builder");

        this.storage = builder.getStorage();
        final long lastId = storage.getLastProducedId();
        this.disruptor = new Disruptor<>(ProducerEvent::new, builder.getRingBufferSize(),
                new NamedThreadFactory("producer-worker-"));
        this.disruptor.handleEventsWith(new ProduceHandler(builder.getBatchSize()));
        this.disruptor.start();
        this.translator = new ProducerTranslator(lastId);
        this.ringBuffer = disruptor.getRingBuffer();
        this.executor = Executors.newSingleThreadExecutor();
        this.serializer = builder.getSerializer();
    }

    /**
     * Produce an object to the queue. This method may block when the downstream storage is too slow and
     * the internal buffer is running out.
     *
     * @param object The object to put into the queue
     * @return a future which will be completed when the object is safely saved or encounter some exceptions
     */
    public CompletableFuture<Void> produce( E object) {
        requireNonNull(object, "object");

        if (closed) {
            return exceptionallyCompletedFuture(new IllegalStateException("producer has been closed"));
        }

        final CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            final byte[] payload = serializer.serialize(object);
            ringBuffer.publishEvent(translator, payload, future, Boolean.FALSE);
        } catch (SerializationException ex) {
            future.completeExceptionally(ex);
        }

        return future;
    }

    public CompletableFuture<Void> flush() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        ringBuffer.publishEvent(translator, null, future, Boolean.TRUE);
        return future;
    }

    @Override
    public void close() throws Exception {
        closed = true;

        final CompletableFuture<Void> future = flush();
        future.join();

        disruptor.shutdown();

        storage.close();
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
            event.future = future;
            if (flush == Boolean.TRUE) {
                event.flush = true;
            }

            if (payload != null) {
                event.objectWithId = new ObjectWithId(nextId.incrementAndGet(), payload);
            }
        }
    }

    private final static class ProducerEvent {
        @Nullable
        private ObjectWithId objectWithId;
        @Nullable
        private CompletableFuture<Void> future;
        private boolean flush;

        void reset() {
            objectWithId = null;
            future = null;
            flush = false;
        }
    }

    private final class ProduceHandler implements EventHandler<ProducerEvent> {
        private final int batchSize;
        private final List<ObjectWithId> batchPayload;
        private final List<CompletableFuture<Void>> batchFutures;

        ProduceHandler(int batchSize) {
            this.batchSize = batchSize;
            this.batchPayload = new ArrayList<>(batchSize);
            this.batchFutures = new ArrayList<>(batchSize);
        }

        @Override
        public void onEvent(ProducerEvent event, long sequence, boolean endOfBatch) {
            if (event.flush) {
                if (!batchPayload.isEmpty()) {
                    flush();
                }
                assert event.future != null;
                executor.execute(() -> event.future.complete(null));
            } else {
                batchPayload.add(event.objectWithId);
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

package com.github.ylgrgyq.reservoir;

import com.github.ylgrgyq.reservoir.LogExceptionHandler.OnEventException;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import static com.spotify.futures.CompletableFutures.exceptionallyCompletedFuture;
import static java.util.Objects.requireNonNull;

public final class ObjectQueueProducer<E> implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ObjectQueueProducer.class);

    private final ProducerStorage storage;
    private final Disruptor<ProducerEvent> disruptor;
    private final RingBuffer<ProducerEvent> ringBuffer;
    private final EventTranslatorThreeArg<ProducerEvent, byte[], CompletableFuture<Void>, Boolean> translator;
    private final ExecutorService executor;
    private final Serializer<E> serializer;
    private volatile boolean closed;

    public ObjectQueueProducer(ObjectQueueProducerBuilder<E> builder) throws StorageException {
        requireNonNull(builder, "builder");

        this.storage = builder.getStorage();
        this.disruptor = new Disruptor<>(ProducerEvent::new, builder.getRingBufferSize(),
                new NamedThreadFactory("producer-worker-"));
        this.disruptor.handleEventsWith(new ProduceHandler(builder.getBatchSize()));
        this.disruptor.setDefaultExceptionHandler(new LogExceptionHandler<>("object-queue-producer",
                (event, ex) -> {
                    if (event.future != null) {
                        event.future.completeExceptionally(ex);
                    }
                }
        ));

        this.disruptor.start();
        this.translator = new

                ProducerTranslator(storage.getLastProducedId());
        this.ringBuffer = disruptor.getRingBuffer();
        this.executor = builder.getExecutorService();
        this.serializer = builder.getSerializer();
    }

    /**
     * Produce an object to the queue. This method may block when the downstream storage is too slow and
     * the internal buffer is running out.
     *
     * @param object The object to put into the queue
     * @return a future which will be completed when the object is safely saved or encounter some exceptions
     */
    public CompletableFuture<Void> produce(E object) {
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

    /**
     * Flush any pending object stayed on the internal buffer.
     *
     * @return a future which will be completed when the flush task is done
     */
    public CompletableFuture<Void> flush() {
        if (closed) {
            return exceptionallyCompletedFuture(new IllegalStateException("producer has been closed"));
        }

        return doFlush();
    }

    @Override
    public void close() throws Exception {
        closed = true;

        final CompletableFuture<Void> future = doFlush();
        future.join();

        disruptor.shutdown();
        executor.shutdown();
        storage.close();
    }

    private CompletableFuture<Void> doFlush() {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        ringBuffer.publishEvent(translator, null, future, Boolean.TRUE);
        return future;
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

        @Override
        public String toString() {
            return "ProducerEvent{" +
                    "objectWithId=" + objectWithId +
                    ", flush=" + flush +
                    '}';
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
            assert event.future != null;

            if (event.flush) {
                if (!batchPayload.isEmpty()) {
                    flush();
                }
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
            try {
                storage.store(batchPayload);

                completeFutures(batchFutures);
            } catch (StorageException ex) {
                completeFutures(batchFutures, ex);
            }

            batchPayload.clear();
            batchFutures.clear();
        }

        private void completeFutures(List<CompletableFuture<Void>> futures) {
            for (final CompletableFuture<Void> future : futures) {
                try {
                    executor.execute(() -> future.complete(null));
                } catch (Exception ex) {
                    logger.error("Submit complete future task failed", ex);
                }
            }
        }

        private void completeFutures(List<CompletableFuture<Void>> futures, Throwable t) {
            for (final CompletableFuture<Void> future : futures) {
                try {
                    executor.execute(() -> future.completeExceptionally(t));
                } catch (Exception ex) {
                    logger.error("Submit complete future task failed", ex);
                }
            }
        }
    }
}

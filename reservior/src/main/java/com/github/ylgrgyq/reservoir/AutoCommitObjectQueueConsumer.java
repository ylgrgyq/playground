package com.github.ylgrgyq.reservoir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

final class AutoCommitObjectQueueConsumer<E> implements ObjectQueueConsumer<E> {
    private static final Logger logger = LoggerFactory.getLogger(ObjectQueueConsumer.class);
    private static final ThreadFactory threadFactory = new NamedThreadFactory("object-queue-consumer-");

    private final ConsumerStorage storage;
    private final BlockingQueue<E> queue;
    private final Thread worker;
    private final int batchSize;

    private volatile boolean closed;

    AutoCommitObjectQueueConsumer(ObjectQueueConsumerBuilder<E> builder) {
        requireNonNull(builder, "builder");

        this.storage = builder.getStorage();
        this.batchSize = builder.getBatchSize();
        this.queue = new ArrayBlockingQueue<>(this.batchSize);
        final long offset = storage.getLastCommittedId();
        this.worker = threadFactory.newThread(new FetchWorker(offset, builder.getDeserializer()));
        this.worker.start();
    }


    public E fetch() throws InterruptedException {
        E obj = queue.poll();
        if (obj == null) {
            obj = queue.take();
        }

        return obj;
    }

    @Nullable
    public E fetch(long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(unit);

        E obj = queue.poll();
        if (obj == null) {
            queue.poll(timeout, unit);
        }

        return obj;
    }

    public void commit() {
        // id of the object at the head of the queue is already committed when it's fetched from the storage
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public void close() throws Exception {
        closed = true;

        if (Thread.currentThread() != worker) {
            worker.interrupt();
            worker.join();
        }

        storage.close();
    }

    private final class FetchWorker implements Runnable {
        private final Deserializer<E> deserializer;
        private long lastId;

        FetchWorker(long lastId, Deserializer<E> deserializer) {
            this.lastId = lastId;
            this.deserializer = deserializer;
        }

        @Override
        public void run() {
            while (!closed) {
                try {
                    final Collection<? extends ObjectWithId> payloads = storage.read(lastId, batchSize);
                    if (!payloads.isEmpty()) {
                        for (ObjectWithId p : payloads) {
                            final byte[] pInBytes = p.getObjectInBytes();
                            try {
                                final E pObj = deserializer.deserialize(pInBytes);
                                queue.put(pObj);
                            } catch (Exception ex) {
                                logger.error("Deserialize payload with id: {} failed. Content in Base64 string is: {}",
                                        lastId, Base64.getEncoder().encodeToString(pInBytes), ex);
                                close();
                                break;
                            }

                            lastId = p.getId();
                        }

                        storage.commitId(lastId);
                    }
                } catch (InterruptedException ex) {
                    // do nothing
                } catch (Exception ex) {
                    logger.error("Fetch worker got unexpected exception", ex);
                }
            }
        }
    }
}

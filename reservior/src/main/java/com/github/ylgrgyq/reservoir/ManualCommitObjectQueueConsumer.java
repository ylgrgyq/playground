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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.requireNonNull;

final class ManualCommitObjectQueueConsumer<E> implements ObjectQueueConsumer<E> {
    private static final Logger logger = LoggerFactory.getLogger(ObjectQueueConsumer.class);
    private static final ThreadFactory threadFactory = new NamedThreadFactory("object-queue-consumer-");

    private final ConsumerStorage storage;
    private final BlockingQueue<E> queue;
    private final Thread worker;
    private final AtomicLong offset;
    private final ReentrantLock lock;
    private final Condition notEmpty;
    private final int batchSize;

    private volatile boolean closed;

    ManualCommitObjectQueueConsumer(ObjectQueueConsumerBuilder<E> builder) throws StorageException {
        requireNonNull(builder, "builder");

        this.storage = builder.getStorage();
        this.batchSize = builder.getBatchSize();
        this.queue = new ArrayBlockingQueue<>(this.batchSize);
        final long offset = storage.getLastCommittedId();
        this.offset = new AtomicLong(offset);
        this.worker = threadFactory.newThread(new FetchWorker(offset, builder.getDeserializer()));
        this.worker.start();
        this.lock = new ReentrantLock();
        this.notEmpty = this.lock.newCondition();
    }


    public E fetch() throws InterruptedException {
        E obj;

        lock.lockInterruptibly();
        try {
            while ((obj = queue.peek()) == null) {
                notEmpty.await();
            }
        } finally {
            lock.unlock();
        }

        return obj;
    }

    @Nullable
    public E fetch(long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(unit);

        E obj = null;

        final long end = System.nanoTime() + unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            long remain;
            while ((remain = end - System.nanoTime()) > 0 && (obj = queue.peek()) == null) {
                notEmpty.await(remain, TimeUnit.NANOSECONDS);
            }
        } finally {
            lock.unlock();
        }

        return obj;
    }

    public void commit() throws StorageException{
        final E payload = queue.poll();
        assert payload != null;
        final long id = offset.incrementAndGet();
        storage.commitId(id);
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
                    final Collection<? extends ObjectWithId> payloads = storage.fetch(lastId, batchSize);
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

                        lock.lock();
                        try {
                            notEmpty.signal();
                        } finally {
                            lock.unlock();
                        }
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

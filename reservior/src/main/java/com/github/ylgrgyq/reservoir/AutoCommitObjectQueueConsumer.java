package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.requireNonNull;

final class AutoCommitObjectQueueConsumer<E> implements ObjectQueueConsumer<E> {
    private final ObjectQueueStorage storage;
    private final BlockingQueue<E> queue;
    private final int batchSize;
    private final ReentrantLock lock;
    private final Codec<E> deserializer;
    private long lastCommittedId;

    private volatile boolean closed;

    AutoCommitObjectQueueConsumer(ObjectQueueBuilder<E> builder) throws StorageException {
        requireNonNull(builder, "builder");

        this.storage = builder.getStorage();
        this.batchSize = builder.getBatchSize();
        this.queue = new ArrayBlockingQueue<>(2 * this.batchSize);
        this.lock = new ReentrantLock();
        this.lastCommittedId = storage.getLastCommittedId();
        this.deserializer = builder.getCodec();
    }

    @Override
    public E fetch() throws InterruptedException, StorageException {
        E obj;
        while ((obj = queue.poll()) == null) {
            blockFetchFromStorage(0, TimeUnit.NANOSECONDS);
        }

        return obj;
    }

    @Nullable
    @Override
    public E fetch(long timeout, TimeUnit unit) throws InterruptedException, StorageException {
        requireNonNull(unit);

        E obj;
        while ((obj = queue.poll()) == null) {
            if (!blockFetchFromStorage(timeout, unit)) {
                break;
            }
        }

        return obj;
    }

    @Override
    public void commit() {
        // id of the object at the head of the queue is already committed when it's fetched from the storage
    }

    @Override
    public boolean closed() {
        return closed;
    }

    @Override
    public void close() throws Exception {
        closed = true;

        lock.lock();
        try {
            storage.close();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Blocking to fetch objects from internal storage.
     *
     * @param timeout the maximum time to wait. 0 is to wait infinitely
     * @param unit    the unit of the wait time
     * @return true when there's some objects in buffer queue, false for timeout or error occurred
     * @throws InterruptedException when interrupted or this consumer is closed
     * @throws StorageException     when some bad things happened in the internal storage
     */
    private boolean blockFetchFromStorage(long timeout, TimeUnit unit) throws InterruptedException, StorageException {
        lock.lock();
        try {
            if (closed) {
                throw new InterruptedException("consumer closed");
            }

            if (!queue.isEmpty()) {
                return true;
            }

            long lastId = lastCommittedId;
            final List<? extends ObjectWithId> payloads;
            if (timeout == 0) {
                payloads = storage.fetch(lastId, batchSize);
            } else {
                payloads = storage.fetch(lastId, batchSize, timeout, unit);
            }

            if (!payloads.isEmpty()) {
                for (ObjectWithId p : payloads) {
                    final byte[] pInBytes = p.getObjectInBytes();
                    try {
                        final E pObj = deserializer.deserialize(pInBytes);
                        queue.put(pObj);
                    } catch (Exception ex) {
                        String msg = "deserialize object with id: " + p.getId() +
                                " failed. Content in Base64 string is: " + Base64.getEncoder().encodeToString(pInBytes);
                        throw new DeserializationException(msg, ex);
                    }

                    lastId = p.getId();
                }

                storage.commitId(lastId);
                lastCommittedId = lastId;
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }
}

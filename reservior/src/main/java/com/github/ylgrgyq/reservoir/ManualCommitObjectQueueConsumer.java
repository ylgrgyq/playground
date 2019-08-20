package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.requireNonNull;

final class ManualCommitObjectQueueConsumer<E> implements ObjectQueueConsumer<E> {
    private final ConsumerStorage storage;
    private final BlockingQueue<DeserializedObjectWithId<E>> queue;
    private final ReentrantLock lock;
    private final Condition notEmpty;
    private final int batchSize;
    private final Deserializer<E> deserializer;
    private long lastCommittedId;

    private volatile boolean closed;

    ManualCommitObjectQueueConsumer(ObjectQueueConsumerBuilder<E> builder) throws StorageException {
        requireNonNull(builder, "builder");

        this.storage = builder.getStorage();
        this.batchSize = builder.getBatchSize();
        this.queue = new ArrayBlockingQueue<>(this.batchSize);
        this.lastCommittedId = storage.getLastCommittedId();
        this.deserializer = builder.getDeserializer();
        this.lock = new ReentrantLock();
        this.notEmpty = this.lock.newCondition();
    }

    @Override
    public E fetch() throws InterruptedException, StorageException {
        DeserializedObjectWithId<E> obj;
        while ((obj = queue.poll()) == null) {
            blockFetchFromStorage(0, TimeUnit.NANOSECONDS);
        }

        return obj.object;
    }

    @Nullable
    @Override
    public E fetch(long timeout, TimeUnit unit) throws InterruptedException, StorageException {
        requireNonNull(unit);

        DeserializedObjectWithId<E> obj;
        while ((obj = queue.poll()) == null) {
            if (!blockFetchFromStorage(timeout, unit)) {
                break;
            }
        }

        if (obj == null) {
            return null;
        } else {
            return obj.object;
        }
    }

    @Override
    public void commit() throws StorageException {
        final DeserializedObjectWithId<E> payload = queue.poll();
        if (payload == null) {
            throw new NoSuchElementException();
        }

        lock.lock();
        try {
            final long id = payload.id;
            if (id > lastCommittedId) {
                lastCommittedId = id;
                storage.commitId(id);
            }
        } finally {
            lock.unlock();
        }
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
            final Collection<? extends ObjectWithId> payloads;
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
                        queue.put(new DeserializedObjectWithId<>(p.getId(), pObj));
                    } catch (Exception ex) {
                        String msg = "deserialize object with id: " + p.getId() +
                                " failed. Content in Base64 string is: " + Base64.getEncoder().encodeToString(pInBytes);
                        throw new DeserializationException(msg, ex);
                    }
                }

                lock.lock();
                try {
                    notEmpty.signal();
                } finally {
                    lock.unlock();
                }
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    private static final class DeserializedObjectWithId<E> {
        private final E object;
        private final long id;

        public DeserializedObjectWithId(long id, E object) {
            this.object = object;
            this.id = id;
        }
    }
}

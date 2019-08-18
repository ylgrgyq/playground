package com.github.ylgrgyq.resender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ResendQueueConsumer<E extends Payload> implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ResendQueueConsumer.class);

    private final ConsumerStorage<PayloadWithId> storage;
    private final BlockingQueue<E> queue;
    private final boolean autoCommit;
    private final Thread worker;
    private final AtomicLong offset;
    private final ReentrantLock lock;
    private final Condition notEmpty;

    private volatile boolean stop;

    public ResendQueueConsumer(ConsumerStorage<PayloadWithId> storage, Deserializer<E> deserializer, boolean autoCommit) {
        this.storage = storage;
        this.queue = new ArrayBlockingQueue<>(1000);
        this.autoCommit = autoCommit;
        long offset = storage.getLastCommittedId();
        this.offset = new AtomicLong(offset);
        this.worker = new NamedThreadFactory("Resend-Queue-Consumer-").newThread(new FetchWorker(offset, deserializer));
        this.lock = new ReentrantLock();
        this.notEmpty = this.lock.newCondition();
    }

    public E fetch() throws InterruptedException {
        E element;

        if (autoCommit) {
            element = queue.poll();
            if (element == null) {
                queue.take();
            }
        } else {
            lock.lockInterruptibly();
            try {
                while ((element = queue.peek()) == null) {
                    notEmpty.await();
                }
            } finally {
                lock.unlock();
            }
        }

        return element;
    }

    public E fetch(long timeout, TimeUnit unit) throws InterruptedException {
        E element = null;

        if (autoCommit) {
            element = queue.poll();
            if (element == null) {
                queue.poll(timeout, unit);
            }
        } else {
            long end = System.nanoTime() + unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (System.nanoTime() < end && (element = queue.peek()) == null) {
                    notEmpty.await(timeout, unit);
                }
            } finally {
                lock.unlock();
            }
        }

        return element;
    }

    public void commit() {
        if (!autoCommit) {
            Payload payload = queue.poll();
            assert payload != null;
            long id = offset.incrementAndGet();
            storage.commitId(id);
        }
    }

    public boolean stopped() {
        return stop;
    }

    @Override
    public void close() throws Exception {
        stop = true;

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
            while (!stop) {
                try {
                    Collection<? extends PayloadWithId> payloads = storage.read(lastId, 1000);
                    if (payloads != null && !payloads.isEmpty()) {
                        for (PayloadWithId p : payloads) {
                            byte[] pInBytes = p.getPayload();
                            try {
                                E pObj = deserializer.deserialize(pInBytes);
                                queue.put(pObj);
                            } catch (DeserializationException ex) {
                                logger.error("Deserialize payload with id: {} failed. Content in Base64 string is: {}",
                                        lastId, Base64.getEncoder().encodeToString(pInBytes), ex);
                                close();
                                break;
                            }

                            lastId = p.getId();
                        }

                        if (!autoCommit) {
                            lock.lock();
                            try {
                                notEmpty.signal();
                            } finally {
                                lock.unlock();
                            }
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

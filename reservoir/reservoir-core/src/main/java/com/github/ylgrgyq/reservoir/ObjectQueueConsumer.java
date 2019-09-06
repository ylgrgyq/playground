package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public interface ObjectQueueConsumer<E> extends AutoCloseable {
    /**
     * Blocking fetch an object of type E from the queue.
     *
     * @return the fetched object
     * @throws InterruptedException
     * @throws StorageException
     */
    E fetch() throws InterruptedException, StorageException;

    @Nullable
    E fetch(long timeout, TimeUnit unit) throws InterruptedException, StorageException;

    void commit() throws StorageException;

    boolean closed();
}

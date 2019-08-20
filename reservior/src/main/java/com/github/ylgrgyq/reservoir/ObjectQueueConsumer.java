package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public interface ObjectQueueConsumer<E> extends AutoCloseable {
    E fetch() throws InterruptedException, StorageException;

    @Nullable
    E fetch(long timeout, TimeUnit unit) throws InterruptedException, StorageException;

    void commit() throws StorageException;

    boolean closed();
}

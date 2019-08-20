package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public interface ObjectQueueConsumer<E> extends AutoCloseable {

    E fetch() throws InterruptedException;

    @Nullable
    E fetch(long timeout, TimeUnit unit) throws InterruptedException;

    void commit();

    boolean closed();
}

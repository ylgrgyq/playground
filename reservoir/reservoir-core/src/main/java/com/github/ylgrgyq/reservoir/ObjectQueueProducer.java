package com.github.ylgrgyq.reservoir;

import java.util.concurrent.CompletableFuture;

public interface ObjectQueueProducer<E> extends AutoCloseable{
    /**
     * Produce an object to the queue. This method may block when the downstream storage is too slow and
     * the internal buffer is running out.
     *
     * @param object The object to put into the queue
     * @return a future which will be completed when the object is safely saved or encounter some exceptions
     */
    CompletableFuture<Void> produce(E object);

    /**
     * Flush any pending object stayed on the internal buffer.
     *
     * @return a future which will be completed when the flush task is done
     */
    CompletableFuture<Void> flush();
}

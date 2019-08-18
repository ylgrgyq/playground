package com.github.ylgrgyq.resender;

import java.util.Collection;

public interface ProducerStorage<E> extends AutoCloseable {
    long getLastProducedId();

    void store(Collection<? extends E> batch);
}

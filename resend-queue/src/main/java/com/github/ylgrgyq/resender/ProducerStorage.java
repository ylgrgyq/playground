package com.github.ylgrgyq.resender;

import java.util.Collection;

public interface ProducerStorage extends AutoCloseable {
    long getLastProducedId();

    void store(Collection<ElementWithId> batch);
}

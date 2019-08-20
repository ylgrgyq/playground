package com.github.ylgrgyq.reservoir;

import java.util.Collection;

public interface ProducerStorage extends AutoCloseable {
    long getLastProducedId();

    void store( Collection<ObjectWithId> batch);
}

package com.github.ylgrgyq.reservoir;

import java.util.Collection;

public interface ProducerStorage extends AutoCloseable {
    long getLastProducedId() throws StorageException;

    void store( Collection<ObjectWithId> batch) throws StorageException;
}

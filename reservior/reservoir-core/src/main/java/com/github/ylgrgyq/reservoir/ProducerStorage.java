package com.github.ylgrgyq.reservoir;

import java.util.List;

public interface ProducerStorage extends AutoCloseable {
    long getLastProducedId() throws StorageException;

    void store(List<ObjectWithId> batch) throws StorageException;
}

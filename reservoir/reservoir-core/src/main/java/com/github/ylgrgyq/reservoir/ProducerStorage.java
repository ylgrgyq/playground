package com.github.ylgrgyq.reservoir;

import java.util.List;

public interface ProducerStorage extends AutoCloseable {
    void store(List<byte[]> batch) throws StorageException;
}

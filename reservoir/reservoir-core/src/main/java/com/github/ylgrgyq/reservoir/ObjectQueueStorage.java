package com.github.ylgrgyq.reservoir;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ObjectQueueStorage extends AutoCloseable {
    void store(List<byte[]> batch) throws StorageException;

    void commitId(long id) throws StorageException;

    long getLastCommittedId() throws StorageException;

    List<ObjectWithId> fetch(long fromId, int limit) throws InterruptedException, StorageException;

    List<ObjectWithId> fetch(long fromId, int limit, long timeout, TimeUnit unit) throws InterruptedException, StorageException;
}

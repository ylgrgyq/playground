package com.github.ylgrgyq.reservoir;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public interface ConsumerStorage extends AutoCloseable{
    void commitId(long id) throws StorageException;

    long getLastCommittedId() throws StorageException;

    Collection<ObjectWithId> fetch(long fromId, int limit) throws InterruptedException, StorageException;

    Collection<ObjectWithId> fetch(long fromId, int limit, long timeout, TimeUnit unit) throws InterruptedException, StorageException;
}

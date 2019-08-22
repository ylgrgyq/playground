package com.github.ylgrgyq.reservoir;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

abstract class AbstractTestingStorage  implements ObjectQueueStorage {
    @Override
    public void commitId(long id) throws StorageException {

    }

    @Override
    public long getLastCommittedId() throws StorageException {
        return 0;
    }

    @Override
    public Collection<ObjectWithId> fetch(long fromId, int limit) throws InterruptedException, StorageException {
        return Collections.emptyList();
    }

    @Override
    public Collection<ObjectWithId> fetch(long fromId, int limit, long timeout, TimeUnit unit) throws InterruptedException, StorageException {
        return Collections.emptyList();
    }

    @Override
    public long getLastProducedId() throws StorageException {
        return 0;
    }

    @Override
    public void store(Collection<ObjectWithId> batch) throws StorageException {

    }

    @Override
    public void close() throws Exception {

    }
}

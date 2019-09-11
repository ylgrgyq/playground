package com.github.ylgrgyq.reservoir;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

abstract class AbstractTestingStorage  implements ObjectQueueStorage<byte[]> {
    @Override
    public void commitId(long id) throws StorageException {

    }

    @Override
    public long getLastCommittedId() throws StorageException {
        return 0;
    }

    @Override
    public List<ObjectWithId<byte[]>> fetch(long fromId, int limit) throws InterruptedException, StorageException {
        return Collections.emptyList();
    }

    @Override
    public List<ObjectWithId<byte[]>> fetch(long fromId, int limit, long timeout, TimeUnit unit) throws InterruptedException, StorageException {
        return Collections.emptyList();
    }

    @Override
    public void store(List<byte[]> batch) throws StorageException {

    }

    @Override
    public void close() throws Exception {

    }
}

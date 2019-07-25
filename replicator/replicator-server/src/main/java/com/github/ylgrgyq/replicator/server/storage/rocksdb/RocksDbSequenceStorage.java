package com.github.ylgrgyq.replicator.server.storage.rocksdb;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.google.protobuf.ByteString;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RocksDbSequenceStorage implements SequenceStorage {
    private Storage<RocksDbStorageHandle> storage;
    private final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private volatile long firstLogId = 1;

    private volatile boolean hasLoadFirstLogId;
    private RocksDbStorageHandle handle;

    public RocksDbSequenceStorage(Storage<RocksDbStorageHandle> storage, RocksDbStorageHandle storageHandle) {
        this.storage = storage;
        this.handle = storageHandle;
    }

    @Override
    public long getFirstLogId() {
        readLock.lock();
        try {
            if (hasLoadFirstLogId) {
                return firstLogId;
            }

            long firstLogId = storage.getFirstLogId(handle);
            setFirstLogId(firstLogId);
            return firstLogId;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long getLastLogId() {
        readLock.lock();
        try {
            long lastId = storage.getFirstLogId(handle);
            if (lastId == 0) {
                lastId = getFirstLogId();
            }
            return lastId;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void append(long id, byte[] data) {
        readLock.lock();
        try {
            LogEntry entry = LogEntry.newBuilder().setData(ByteString.copyFrom(data)).build();
            storage.append(handle, id, entry.toByteArray());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<LogEntry> getEntries(long fromId, int limit) {
        readLock.lock();
        try {
            return storage.getEntries(handle, fromId, limit);
        } finally {
            readLock.unlock();
        }
    }

    private void setFirstLogId(long id) {
        firstLogId = id;
        hasLoadFirstLogId = true;
    }

    @Override
    public void trimToId(long id) {
        long idToKeep = storage.trimToId(handle, id);
        setFirstLogId(idToKeep);
    }

    @Override
    public void shutdown() {
        writeLock.lock();
        try {
            handle.getColumnFailyHandle().close();
        } finally {
            writeLock.unlock();
        }
    }
}

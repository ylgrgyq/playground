package com.github.ylgrgyq.replicator.server.storage.rocksdb;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import com.google.protobuf.ByteString;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RocksDbSequenceStorage implements SequenceStorage {
    private final Storage<RocksDbStorageHandle> storage;
    private final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final RocksDbStorageHandle handle;

    private volatile long firstLogId;
    private volatile boolean hasLoadFirstLogId;

    public RocksDbSequenceStorage(Storage<RocksDbStorageHandle> storage, RocksDbStorageHandle storageHandle) {
        Objects.requireNonNull(storage);
        Objects.requireNonNull(storageHandle);

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

            long fid = storage.getFirstLogId(handle);
            if (fid != 0) {
                setFirstLogId(fid);
            }
            return fid;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long getLastLogId() {
        readLock.lock();
        try {
            long lastId = storage.getLastLogId(handle);
            // lastId = 0 means internal storage don't have any logs in it.
            // maybe we haven't append any log or old logs have been trimmed.
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
            LogEntry entry = LogEntry.newBuilder()
                    .setId(id)
                    .setData(ByteString.copyFrom(data))
                    .build();
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
        readLock.lock();
        try {
            long idToKeep = storage.trimToId(handle, id);
            if (idToKeep > firstLogId) {
                setFirstLogId(idToKeep);
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void drop() {
        writeLock.lock();
        try {
            storage.dropSequenceStorage(handle);
        } finally {
            writeLock.unlock();
        }
    }
}

package com.github.ylgrgyq.server;

import com.github.ylgrgyq.proto.LogEntry;
import com.github.ylgrgyq.proto.Snapshot;
import com.github.ylgrgyq.proto.SyncLogEntries;
import com.github.ylgrgyq.server.storage.MemoryStorage;
import com.github.ylgrgyq.server.storage.Storage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Sequence {
    private Storage storage;
    private long pendingSize;
    private SnapshotGenerator snapshotGenerator;
    private Snapshot lastSnapshot;
    private long maxPendingLogSize;
    private ExecutorService executor;
    private Lock readLock;
    private Lock writeLock;
    private AtomicBoolean generateSnapshotJobScheduled;

    public Sequence(String topic, SequenceOptions options) {
        this.snapshotGenerator = options.getSnapshotGenerator();
        this.storage = new MemoryStorage(topic);
        this.maxPendingLogSize = options.getMaxPendingLogSize();
        this.executor = options.getSequenceExecutor();
        this.generateSnapshotJobScheduled = new AtomicBoolean(false);

        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
    }

    public void init() {
        storage.init();
    }

    public void append(byte[] data) {
        writeLock.lock();
        try {
            storage.append(data);
            pendingSize = pendingSize + data.length;

            if (pendingSize >= maxPendingLogSize) {
                scheduleGenerateSnapshot();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public SyncLogEntries syncLogs(long fromIndex, int limit) {
        readLock.lock();
        try {
            if (lastSnapshot != null && fromIndex < lastSnapshot.getIndex()) {
                throw new ReplicatorException(ReplicatorError.ENEEDCATCHUP);
            }
        } finally {
            readLock.unlock();
        }

        List<LogEntry> entries = storage.getEntries(fromIndex, limit);
        SyncLogEntries.Builder builder = SyncLogEntries.newBuilder();
        builder.addAllEntries(entries);
        return builder.build();
    }

    public Snapshot getSnapshot() {
        readLock.lock();
        try {
            if (lastSnapshot != null) {
                return lastSnapshot;
            }
        } finally {
            readLock.unlock();
        }
        return null;
    }

    private void scheduleGenerateSnapshot() {
        if (generateSnapshotJobScheduled.compareAndSet(false, true)) {
            executor.submit(() -> {
                Snapshot snapshot = snapshotGenerator.generateSnapshot();
                writeLock.lock();
                try {
                    storage.trimToIndex(lastSnapshot.getIndex());
                    lastSnapshot = snapshot;
                    pendingSize = storage.pendingLogSize();
                    generateSnapshotJobScheduled.set(false);
                } finally {
                    writeLock.unlock();
                }
            });
        }
    }
}

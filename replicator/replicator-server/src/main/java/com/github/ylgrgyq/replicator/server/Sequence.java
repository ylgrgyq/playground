package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;
import com.github.ylgrgyq.replicator.server.storage.MemoryStorage;
import com.github.ylgrgyq.replicator.server.storage.RocksDbStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Sequence {
    private static final Logger logger = LoggerFactory.getLogger(Sequence.class);

    private static final Snapshot emptySnapshot;

    static {
        Snapshot.Builder builder = Snapshot.newBuilder();
        builder.setId(0);
        emptySnapshot = builder.build();
    }

    private String topic;
    private Storage storage;
    private final SnapshotGenerator snapshotGenerator;
    private Snapshot lastSnapshot;
    private SequenceOptions options;
    private ScheduledExecutorService executor;
    private Lock readLock;
    private Lock writeLock;
    private AtomicBoolean generateSnapshotJobScheduled;

    public Sequence(String topic, SequenceOptions options) {
        this.topic = topic;
        this.options = options;
        this.snapshotGenerator = options.getSnapshotGenerator();
        this.storage = new RocksDbStorage(options.getStoragePath());
        this.executor = options.getSequenceExecutor();
        this.generateSnapshotJobScheduled = new AtomicBoolean(false);

        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
    }

    public void init() {
        if (executor != null && snapshotGenerator != null) {
            scheduleGenerateSnapshot();
        }
    }

    public void append(long id, byte[] data) {
        readLock.lock();
        try {
            storage.append(id, data);
        } finally {
            readLock.unlock();
        }
    }

    public SyncLogEntries syncLogs(long fromId, int limit) {
        List<LogEntry> entries = storage.getEntries(fromId, limit);

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

        return emptySnapshot;
    }

    private void scheduleGenerateSnapshot() {
        executor.scheduleWithFixedDelay(() -> {
            if (generateSnapshotJobScheduled.compareAndSet(false, true)) {
                Snapshot snapshot = snapshotGenerator.generateSnapshot();
                writeLock.lock();
                try {
                    storage.trimToId(lastSnapshot.getId());
                    lastSnapshot = snapshot;
                    generateSnapshotJobScheduled.set(false);
                } finally {
                    writeLock.unlock();
                    generateSnapshotJobScheduled.set(false);
                }
            } else {
                logger.warn("");
            }
        }, options.getGenerateSnapshotIntervalSecs(), options.getGenerateSnapshotIntervalSecs(), TimeUnit.SECONDS);
    }
}

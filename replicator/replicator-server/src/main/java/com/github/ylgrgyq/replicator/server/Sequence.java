package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;
import com.github.ylgrgyq.replicator.server.storage.MemoryStorage;
import com.github.ylgrgyq.replicator.server.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Sequence {
    private static final Logger logger = LoggerFactory.getLogger(Sequence.class);

    private final Snapshot emptySnapshot;
    private String topic;
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
        this.topic = topic;
        Snapshot.Builder builder = Snapshot.newBuilder();
        builder.setTopic(topic);
        builder.setId(-1);
        this.emptySnapshot = builder.build();

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

    public void append(long id, byte[] data) {
        writeLock.lock();
        try {
            storage.append(id, data);
            pendingSize = pendingSize + data.length;

            if (pendingSize >= maxPendingLogSize) {
                scheduleGenerateSnapshot();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public SyncLogEntries syncLogs(long fromIndex, int limit) {
        logger.info("sy {} {} {}", fromIndex, limit);

        List<LogEntry> entries = storage.getEntries(fromIndex, limit);
        logger.info("sy2 {} {} {}", fromIndex, limit, entries);

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
        if (generateSnapshotJobScheduled.compareAndSet(false, true)) {
            executor.submit(() -> {
                Snapshot snapshot = snapshotGenerator.generateSnapshot();
                writeLock.lock();
                try {
                    storage.trimToId(lastSnapshot.getId());
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

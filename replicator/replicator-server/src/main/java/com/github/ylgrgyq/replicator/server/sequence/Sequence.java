package com.github.ylgrgyq.replicator.server.sequence;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;
import com.github.ylgrgyq.replicator.server.SnapshotGenerator;
import com.github.ylgrgyq.replicator.server.storage.SequenceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Sequence implements SequenceAppender, SequenceReader {
    private static final Logger logger = LoggerFactory.getLogger(Sequence.class);

    private static final Snapshot emptySnapshot;

    static {
        Snapshot.Builder builder = Snapshot.newBuilder();
        builder.setId(0);
        emptySnapshot = builder.build();
    }

    private final String topic;
    private final SequenceStorage storage;
    private final SnapshotGenerator snapshotGenerator;
    private final SequenceOptions options;
    private final ScheduledExecutorService executor;
    private final Lock readLock;
    private final Lock writeLock;
    private final AtomicBoolean generateSnapshotJobScheduled;
    private final ScheduledFuture<?> generateSnapshotFuture;
    private Snapshot lastSnapshot;

    /**
     * @throws java.util.concurrent.RejectedExecutionException if the executor for generating snapshot is full
     */
    public Sequence(String topic, ScheduledExecutorService executor, SequenceStorage sequenceStorage, SequenceOptions options) {
        this.topic = topic;
        this.options = options;
        this.snapshotGenerator = options.getSnapshotGenerator();
        this.storage = sequenceStorage;
        this.executor = executor;
        this.generateSnapshotJobScheduled = new AtomicBoolean(false);

        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();

        if (snapshotGenerator != null) {
            generateSnapshotFuture = scheduleGenerateSnapshot();
        } else {
            generateSnapshotFuture = null;
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

    public Snapshot getLastSnapshot() {
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

    private ScheduledFuture<?> scheduleGenerateSnapshot() {
        return executor.scheduleWithFixedDelay(() -> {
            if (generateSnapshotJobScheduled.compareAndSet(false, true)) {
                Snapshot snapshot = null;
                try {
                    snapshot = snapshotGenerator.generateSnapshot();
                } catch (Exception ex) {
                    logger.error("generate snapshot failed", ex);
                }

                if (snapshot != null) {
                    writeLock.lock();
                    try {
                        storage.trimToId(snapshot.getId());
                        lastSnapshot = snapshot;
                        generateSnapshotJobScheduled.set(false);
                    } catch (Exception ex) {
                        logger.error("trim to id {} failed after generate snapshot", lastSnapshot, ex);
                    } finally {
                        writeLock.unlock();
                        generateSnapshotJobScheduled.set(false);
                    }
                }
            } else {
                logger.warn("Failed to schedule task to generate snapshot because last generating snapshot task is still running.");
            }
        }, options.getGenerateSnapshotIntervalSecs(), options.getGenerateSnapshotIntervalSecs(), TimeUnit.SECONDS);
    }

    public void shutdown() throws InterruptedException {
        if (generateSnapshotFuture != null) {
            generateSnapshotFuture.cancel(false);
            while (true) {
                try {
                    // schedule and wait shutdown job done
                    executor.submit(() -> null).get();
                    break;
                } catch (ExecutionException ex) {
                    // it's safe to ignore ExecutionException
                } catch (RejectedExecutionException ex) {
                    // retry
                    Thread.sleep(1000);
                }
            }
        }
    }

    public void drop() {
        storage.drop();
    }
}

package com.github.ylgrgyq.replicator.server.sequence;

import com.github.ylgrgyq.replicator.common.ReplicatorError;
import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import com.github.ylgrgyq.replicator.proto.BatchLogEntries;
import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.proto.Snapshot;
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

public class SequenceImpl implements Sequence {
    private static final Logger logger = LoggerFactory.getLogger(SequenceImpl.class);

    private static final Snapshot emptySnapshot;

    static {
        Snapshot.Builder builder = Snapshot.newBuilder();
        builder.setId(0);
        emptySnapshot = builder.build();
    }

    private final SequenceStorage storage;
    private final SnapshotGenerator snapshotGenerator;
    private final SequenceOptions options;
    private final ScheduledExecutorService executor;
    private final Lock readLock;
    private final Lock writeLock;
    private final AtomicBoolean generateSnapshotJobScheduled;
    private final ScheduledFuture<?> generateSnapshotFuture;
    private Snapshot lastSnapshot;
    private volatile boolean stop;

    /**
     * @throws java.util.concurrent.RejectedExecutionException if the executor for generating snapshot is full
     */
    public SequenceImpl(ScheduledExecutorService executor, SequenceStorage sequenceStorage, SequenceOptions options) {
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

    @Override
    public void append(long id, byte[] data) {
        if (stop) {
            throw new ReplicatorException(ReplicatorError.EALREADY_SHUTDOWN);
        }

        readLock.lock();
        try {
            storage.append(id, data);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public BatchLogEntries getLogs(long fromId, int limit) {
        if (stop) {
            throw new ReplicatorException(ReplicatorError.EALREADY_SHUTDOWN);
        }

        List<LogEntry> entries = storage.getEntries(fromId, limit);

        BatchLogEntries.Builder builder = BatchLogEntries.newBuilder();
        builder.addAllEntries(entries);
        return builder.build();
    }

    @Override
    public Snapshot getLastSnapshot() {
        if (stop) {
            throw new ReplicatorException(ReplicatorError.EALREADY_SHUTDOWN);
        }

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

    @Override
    public void shutdown() throws InterruptedException {
        if (stop) {
            return;
        }

        stop = true;

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

    @Override
    public void drop() {
        if (stop) {
            throw new ReplicatorException(ReplicatorError.EALREADY_SHUTDOWN);
        }

        storage.drop();
    }
}

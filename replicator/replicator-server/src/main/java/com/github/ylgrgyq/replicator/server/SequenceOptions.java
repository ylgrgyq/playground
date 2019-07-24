package com.github.ylgrgyq.replicator.server;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SequenceOptions {
    private SnapshotGenerator snapshotGenerator;
    private long maxSnapshotInterval = Long.MAX_VALUE;
    private long maxPendingLogSize;
    private ScheduledExecutorService sequenceExecutor = Executors.newSingleThreadScheduledExecutor();
    private String storagePath;
    private long generateSnapshotIntervalSecs = 10;

    public long getMaxSnapshotInterval() {
        return maxSnapshotInterval;
    }

    public SnapshotGenerator getSnapshotGenerator() {
        return snapshotGenerator;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public long getGenerateSnapshotIntervalSecs() {
        return generateSnapshotIntervalSecs;
    }

    public long getMaxPendingLogSize() {
        return maxPendingLogSize;
    }

    public ScheduledExecutorService getSequenceExecutor() {
        return sequenceExecutor;
    }

    public void setSnapshotGenerator(SnapshotGenerator snapshotGenerator) {
        this.snapshotGenerator = snapshotGenerator;
    }

    public void setMaxSnapshotInterval(long maxSnapshotInterval) {
        this.maxSnapshotInterval = maxSnapshotInterval;
    }

    public void setMaxPendingLogSize(long maxPendingLogSize) {
        this.maxPendingLogSize = maxPendingLogSize;
    }

    public void setSequenceExecutor(ScheduledExecutorService sequenceExecutor) {
        this.sequenceExecutor = sequenceExecutor;
    }
}

package com.github.ylgrgyq.replicator.server;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class SequenceOptions {
    private SnapshotGenerator snapshotGenerator;
    private long maxSnapshotInterval = Long.MAX_VALUE;
    private long maxPendingLogSize;
    private ExecutorService sequenceExecutor;

    public long getMaxSnapshotInterval() {
        return maxSnapshotInterval;
    }

    public SnapshotGenerator getSnapshotGenerator() {
        return snapshotGenerator;
    }

    public long getMaxPendingLogSize() {
        return maxPendingLogSize;
    }

    public ExecutorService getSequenceExecutor() {
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

    public void setSequenceExecutor(ExecutorService sequenceExecutor) {
        this.sequenceExecutor = sequenceExecutor;
    }
}

package com.github.ylgrgyq.replicator.server;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class SequenceOptions {
    private SnapshotGenerator snapshotGenerator;
    private long maxPendingLogSize;
    private ExecutorService sequenceExecutor;

    public SnapshotGenerator getSnapshotGenerator() {
        return snapshotGenerator;
    }

    public long getMaxPendingLogSize() {
        return maxPendingLogSize;
    }

    public ExecutorService getSequenceExecutor() {
        return sequenceExecutor;
    }
}

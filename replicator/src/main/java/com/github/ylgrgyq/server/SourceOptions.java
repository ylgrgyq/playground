package com.github.ylgrgyq.server;

public class SourceOptions {
    private SnapshotGenerator snapshotGenerator;
    private long maxPendingLogSize;

    public SnapshotGenerator getSnapshotGenerator() {
        return snapshotGenerator;
    }

    public long getMaxPendingLogSize() {
        return maxPendingLogSize;
    }
}

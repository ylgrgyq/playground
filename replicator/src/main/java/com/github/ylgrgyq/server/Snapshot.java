package com.github.ylgrgyq.server;

public class Snapshot {
    private byte[] snapshotData;
    private Index snapshotIndex;

    public Snapshot(byte[] snapshotData, Index snapshotIndex) {
        this.snapshotData = snapshotData;
        this.snapshotIndex = snapshotIndex;
    }

    public byte[] getSnapshotData() {
        return snapshotData;
    }

    public Index getSnapshotIndex() {
        return snapshotIndex;
    }
}

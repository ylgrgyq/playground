package com.github.ylgrgyq.replicator.server;


import com.github.ylgrgyq.replicator.proto.Snapshot;

import java.util.List;

public class SyncLog {
    private Snapshot snapshot;
    private List<byte[]> logs;

    public SyncLog(Snapshot snapshot, List<byte[]> logs) {
        this.snapshot = snapshot;
        this.logs = logs;
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public List<byte[]> getLogs() {
        return logs;
    }
}
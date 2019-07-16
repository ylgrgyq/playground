package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;

public interface ReplicateChannel {
    void writeSyncLog(SyncLogEntries log);
    void writeSnapshot(Snapshot snapshot);
    void writeError(ReplicatorError error);
    void writeHandshakeResult();
}

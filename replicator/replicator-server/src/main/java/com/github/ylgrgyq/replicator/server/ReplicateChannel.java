package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.BatchLogEntries;
import com.github.ylgrgyq.replicator.proto.Snapshot;

public interface ReplicateChannel {
    void writeSyncLog(BatchLogEntries log);
    void writeSnapshot(Snapshot snapshot);
    void writeError(ReplicatorError error);
    void writeHandshakeResult();
}

package com.github.ylgrgyq.replicator.server.sequence;

import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.SyncLogEntries;

public interface SequenceReader {
    SyncLogEntries syncLogs(long fromId, int limit);

    Snapshot getLastSnapshot();
}

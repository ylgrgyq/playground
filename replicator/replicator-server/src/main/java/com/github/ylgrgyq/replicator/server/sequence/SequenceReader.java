package com.github.ylgrgyq.replicator.server.sequence;

import com.github.ylgrgyq.replicator.proto.BatchLogEntries;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.github.ylgrgyq.replicator.proto.BatchLogEntries;

public interface SequenceReader {
    BatchLogEntries getLogs(long fromId, int limit);

    Snapshot getLastSnapshot();
}

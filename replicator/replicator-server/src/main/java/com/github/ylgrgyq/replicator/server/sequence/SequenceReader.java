package com.github.ylgrgyq.replicator.server.sequence;

import com.github.ylgrgyq.replicator.common.entity.LogEntry;
import com.github.ylgrgyq.replicator.common.entity.Snapshot;

import java.util.List;

public interface SequenceReader {
    List<LogEntry> getLogs(long fromId, int limit);

    Snapshot getLastSnapshot();
}

package com.github.ylgrgyq.replicator.server.storage;

import com.github.ylgrgyq.replicator.proto.LogEntry;

import java.util.List;

public interface SequenceStorage {
    long getFirstLogId();

    long getLastLogId();

    void append(long id, byte[] data);

    List<LogEntry> getEntries(long fromId, int limit);

    void trimToId(long id);

    void shutdown();
}

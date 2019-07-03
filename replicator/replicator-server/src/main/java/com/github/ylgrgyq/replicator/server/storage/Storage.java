package com.github.ylgrgyq.replicator.server.storage;


import com.github.ylgrgyq.replicator.proto.LogEntry;

import java.util.List;

public interface Storage {
    void init();
    long getFirstIndex();
    long getLastIndex();
    void append(byte[] data);
    List<LogEntry> getEntries(long fromIndex, int limit);
    long pendingLogSize();
    void trimToIndex(long index);
}

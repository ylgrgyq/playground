package com.github.ylgrgyq.server.storage;

import com.github.ylgrgyq.proto.LogEntry;

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

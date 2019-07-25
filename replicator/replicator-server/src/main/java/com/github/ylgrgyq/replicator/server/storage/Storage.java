package com.github.ylgrgyq.replicator.server.storage;


import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.server.SequenceOptions;

import java.util.List;

public interface Storage<T extends StorageHandle> {
    boolean init();
    SequenceStorage createSequenceStorage(String topic, SequenceOptions options);
    void append(T handle, long id, byte[] data);
    List<LogEntry> getEntries(T handle, long fromId, int limit);
    long trimToId(T handle, long id);
    void shutdown();

    long getFirstLogId(T handle);
    long getLastLogId(T handle);
}

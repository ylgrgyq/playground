package com.github.ylgrgyq.replicator.server.storage;


import com.github.ylgrgyq.replicator.common.entity.LogEntry;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;

import java.util.List;

public interface Storage<T extends StorageHandle> {
    SequenceStorage createSequenceStorage(String topic, SequenceOptions options);

    void dropSequenceStorage(T handle);

    void append(T handle, long id, byte[] data);

    List<LogEntry> getEntries(T handle, long fromId, int limit);

    long trimToId(T handle, long id);

    void shutdown() throws InterruptedException;

    long getFirstLogId(T handle);

    long getLastLogId(T handle);
}

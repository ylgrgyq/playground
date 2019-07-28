package com.github.ylgrgyq.replicator.server.storage;


import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.github.ylgrgyq.replicator.server.Preconditions;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

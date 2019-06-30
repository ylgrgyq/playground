package com.github.ylgrgyq.server.storage;

import java.util.List;

public interface Storage {
    void init();
    long getFirstIndex();
    long getLastIndex();
    void append(byte[] data);
    List<byte[]> getEntries(long fromIndex, int limit);
    long pendingLogSize();
    void trimToIndex(long index);
}

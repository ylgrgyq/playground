package com.github.ylgrgyq.server.storage;

import com.github.ylgrgyq.server.Index;

import java.util.ArrayList;
import java.util.List;

public interface Storage {
    void init();
    void append(byte[] data);
    List<byte[]> getEntries(Index fromIndex, int limit);
    void trimToIndex(Index index);
}

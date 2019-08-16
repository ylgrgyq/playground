package com.github.ylgrgyq.resender;

import java.util.Collection;

public interface BackupStorage<E> {
    long getLastId();

    Collection<? extends E> read(long fromId, int limit);

    void store(Collection<? extends E> queue);

    void shutdown() throws Exception;
}

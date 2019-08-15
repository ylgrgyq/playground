package com.github.ylgrgyq.resender;

import java.util.Collection;

public interface BackupStorage<E> {
    long getLastId();

    Collection<? extends E> read();

    void store(Collection<? super E> queue);

    void shutdown() throws Exception;
}

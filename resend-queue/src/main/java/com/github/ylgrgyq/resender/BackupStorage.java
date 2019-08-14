package com.github.ylgrgyq.resender;

import java.util.Collection;

public interface BackupStorage<E> {
    Collection<? extends E> read();

    void store(Collection<? super E> queue);
}

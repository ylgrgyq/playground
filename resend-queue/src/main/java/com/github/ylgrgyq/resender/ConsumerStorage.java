package com.github.ylgrgyq.resender;

import java.util.Collection;

public interface ConsumerStorage<E> extends AutoCloseable{
    void commitId(long id);

    long getLastCommittedId();

    Collection<? extends E> read(long fromId, int limit);
}

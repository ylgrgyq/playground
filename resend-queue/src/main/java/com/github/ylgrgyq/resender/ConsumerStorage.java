package com.github.ylgrgyq.resender;

import java.util.Collection;

public interface ConsumerStorage extends AutoCloseable{
    void commitId(long id);

    long getLastCommittedId();

    Collection<PayloadWithId> read(long fromId, int limit) throws InterruptedException;
}
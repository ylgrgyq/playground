package com.github.ylgrgyq.resender;

import javax.annotation.Nonnull;
import java.util.Collection;

public interface ConsumerStorage extends AutoCloseable{
    void commitId(long id);

    long getLastCommittedId();

    @Nonnull
    Collection<ObjectWithId> read(long fromId, int limit) throws InterruptedException;
}

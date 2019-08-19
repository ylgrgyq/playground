package com.github.ylgrgyq.resender;

import javax.annotation.Nonnull;
import java.util.Collection;

public interface ProducerStorage extends AutoCloseable {
    long getLastProducedId();

    void store(@Nonnull Collection<ObjectWithId> batch);
}

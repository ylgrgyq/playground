package com.github.ylgrgyq.resender;

import javax.annotation.Nonnull;

public interface Serializer<T> {
    @Nonnull
    byte[] serialize(@Nonnull T obj) throws SerializationException;
}

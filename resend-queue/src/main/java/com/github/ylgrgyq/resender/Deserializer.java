package com.github.ylgrgyq.resender;

import javax.annotation.Nonnull;

public interface Deserializer<T> {
    @Nonnull
    T deserialize(@Nonnull byte[] bytes) throws DeserializationException;
}

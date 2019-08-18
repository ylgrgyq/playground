package com.github.ylgrgyq.resender;

public interface Deserializer<T> {
    T deserialize(byte[] bytes) throws DeserializationException;
}

package com.github.ylgrgyq.reservoir;

public interface Deserializer<T> {
    T deserialize(byte[] bytes) throws DeserializationException;
}

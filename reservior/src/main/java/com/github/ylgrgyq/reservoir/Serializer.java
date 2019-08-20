package com.github.ylgrgyq.reservoir;

public interface Serializer<T> {
    byte[] serialize(T obj) throws SerializationException;
}

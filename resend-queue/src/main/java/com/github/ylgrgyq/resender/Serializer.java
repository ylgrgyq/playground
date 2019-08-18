package com.github.ylgrgyq.resender;

public interface Serializer<T> {
    byte[] serialize(T obj) throws SerializationException;
}

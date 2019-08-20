package com.github.ylgrgyq.reservoir;

public interface Serializer<T> {
    /**
     * Serialize an object of type T to a bytes array.
     *
     * @param obj an object to serialize
     * @return a bytes array
     * @throws SerializationException when the object can not serialize to a byte array.
     */
    byte[] serialize(T obj) throws SerializationException;
}

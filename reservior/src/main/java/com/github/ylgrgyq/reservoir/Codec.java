package com.github.ylgrgyq.reservoir;

public interface Codec<T> {
    /**
     * Serialize an object of type T to a bytes array.
     *
     * @param obj an object to serialize
     * @return a bytes array
     * @throws SerializationException when the object can not serialize to a byte array.
     */
    byte[] serialize(T obj) throws SerializationException;

    /**
     * Deserialize a bytes array to an object of type T.
     *
     * @param bytes a bytes array to deserialize
     * @return an object of type T
     * @throws DeserializationException when the bytes array can not deserialize to the expect object of type T,
     *                                  like bytes underflow, etc.
     */
    T deserialize(byte[] bytes) throws DeserializationException;
}

package com.github.ylgrgyq.reservoir;

public interface Deserializer<T> {
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

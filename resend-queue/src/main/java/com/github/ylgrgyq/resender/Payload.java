package com.github.ylgrgyq.resender;

public interface Payload {
    boolean isValid();
    byte[] serialize();
    void deserialize(byte[] bytes);

}

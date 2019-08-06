package com.github.ylgrgyq.replicator.common.entity;

public class HandshakeResponse {
    public byte[] serialize() {
        return new byte[0];
    }

    public void deserialize(byte[] content) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "HandshakeResponse{" +
                super.toString() +
                '}';
    }
}

package com.github.ylgrgyq.resender;

public final class ObjectWithId {
    private final long id;
    private final byte[] objectInBytes;

    public ObjectWithId(long id, byte[] objectInBytes) {
        this.id = id;
        this.objectInBytes = objectInBytes;
    }

    public long getId() {
        return id;
    }

    public byte[] getObjectInBytes() {
        return objectInBytes;
    }
}

package com.github.ylgrgyq.reservoir;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

public final class ObjectWithId {
    private final long id;
    private final byte[] objectInBytes;

    public ObjectWithId(long id, byte[] objectInBytes) {
        Objects.requireNonNull(objectInBytes, "objectInBytes");

        this.id = id;
        this.objectInBytes = Arrays.copyOf(objectInBytes, objectInBytes.length);
    }

    public long getId() {
        return id;
    }

    public byte[] getObjectInBytes() {
        return objectInBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectWithId that = (ObjectWithId) o;
        return getId() == that.getId() &&
                Arrays.equals(getObjectInBytes(), that.getObjectInBytes());
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(getId());
        result = 31 * result + Arrays.hashCode(getObjectInBytes());
        return result;
    }

    @Override
    public String toString() {
        return "ObjectWithId{" +
                "id=" + id +
                ", objectInBytes=" + Base64.getEncoder().encodeToString(objectInBytes) +
                '}';
    }
}

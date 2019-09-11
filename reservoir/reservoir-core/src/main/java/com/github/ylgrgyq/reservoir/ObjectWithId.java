package com.github.ylgrgyq.reservoir;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

public final class ObjectWithId<S> {
    private final long id;
    private final S serializedObject;

    public ObjectWithId(long id, S serializedObject) {
        Objects.requireNonNull(serializedObject, "serializedObject");

        this.id = id;
        this.serializedObject = serializedObject;
    }

    public long getId() {
        return id;
    }

    public S getSerializedObject() {
        return serializedObject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ObjectWithId<?> that = (ObjectWithId<?>) o;
        return getId() == that.getId() &&
                getSerializedObject().getClass() == that.getSerializedObject().getClass() &&
                (getSerializedObject() instanceof byte[] ?
                        Arrays.equals((byte[]) getSerializedObject(),
                                (byte[]) that.getSerializedObject()) :
                        getSerializedObject().equals(that.getSerializedObject()));
    }

    @Override
    public int hashCode() {
        S obj = getSerializedObject();
        if (obj instanceof byte[]) {
            int result = Objects.hash(getId());
            result = 31 * result + Arrays.hashCode((byte[]) obj);
            return result;
        } else {
            return Objects.hash(getId(), obj);
        }
    }

    @Override
    public String toString() {
        return "ObjectWithId{" +
                "id=" + id +
                ", serializedObject=" + (serializedObject instanceof byte[] ?
                Base64.getEncoder().encodeToString((byte[]) serializedObject) :
                serializedObject) +
                '}';
    }
}

package com.github.ylgrgyq.replicator.common;

import com.github.ylgrgyq.replicator.common.exception.DeserializationException;

import java.util.Arrays;
import java.util.Objects;

public class Snapshot {
    private static final int MINIMUM_LENGTH = 12;

    private long id;
    private byte[] data;

    public Snapshot() {
        this.data = new byte[0];
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] serialize() {
        byte[] buffer = new byte[Long.BYTES + Integer.BYTES + data.length];

        Bits.putLong(buffer, 0, id);
        Bits.putInt(buffer, 8, data.length);
        System.arraycopy(data, 0, buffer, 12, data.length);

        return buffer;
    }

    public void deserialize(byte[] content) throws DeserializationException {
        if (content != null && content.length >= MINIMUM_LENGTH) {
            id = Bits.getLong(content, 0);
            int size = Bits.getInt(content, 8);

            data = new byte[size];
            System.arraycopy(content, 12, data, 0, size);
        } else {
            throw new DeserializationException("Snapshot buffer underflow");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Snapshot snapshot = (Snapshot) o;
        return getId() == snapshot.getId() &&
                Arrays.equals(getData(), snapshot.getData());
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(getId());
        result = 31 * result + Arrays.hashCode(getData());
        return result;
    }

    @Override
    public String toString() {
        return "Snapshot{" +
                "id=" + id +
                ", dataLength=" + data.length +
                '}';
    }
}

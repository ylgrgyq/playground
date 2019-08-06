package com.github.ylgrgyq.replicator.common.entity;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;

import java.util.Objects;

public class FetchLogsRequest {
    private static final int MINIMUM_LENGTH = 12;
    private long fromId;
    private int limit;

    public long getFromId() {
        return fromId;
    }

    public void setFromId(long fromId) {
        this.fromId = fromId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public byte[] serialize() {
        byte[] buffer = new byte[Long.BYTES + Integer.BYTES];

        Bits.putLong(buffer, 0, fromId);
        Bits.putInt(buffer, 8, limit);
        return buffer;
    }

    public void deserialize(byte[] content) throws DeserializationException {
        if (content != null && content.length >= MINIMUM_LENGTH) {
            fromId = Bits.getLong(content, 0);
            limit = Bits.getInt(content, 8);
        } else {
            throw new DeserializationException("Fetch logs request command buffer underflow");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FetchLogsRequest that = (FetchLogsRequest) o;
        return getFromId() == that.getFromId() &&
                getLimit() == that.getLimit();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getFromId(), getLimit());
    }

    @Override
    public String toString() {
        return "FetchLogsRequest{" +
                super.toString() +
                "fromId=" + fromId +
                ", limit=" + limit +
                '}';
    }
}

package com.github.ylgrgyq.replicator.common.commands;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;

import java.util.Objects;

@CommandFactoryManager.AutoLoad
public final class FetchLogsRequestCommand extends RequestCommand {
    private static final int MINIMUM_LENGTH = 12;
    private static final byte VERSION = 1;

    private long fromId;
    private int limit;

    public FetchLogsRequestCommand() {
        super(VERSION);
    }

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

    @Override
    public MessageType getMessageType() {
        return MessageType.FETCH_LOGS;
    }

    @Override
    public void serialize() {
        byte[] buffer = new byte[Long.BYTES + Integer.BYTES];

        Bits.putLong(buffer, 0, fromId);
        Bits.putInt(buffer, 8, limit);
        setContent(buffer);
    }

    @Override
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
        FetchLogsRequestCommand that = (FetchLogsRequestCommand) o;
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

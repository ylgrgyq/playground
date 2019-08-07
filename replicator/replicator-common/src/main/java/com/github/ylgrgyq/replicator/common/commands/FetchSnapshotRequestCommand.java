package com.github.ylgrgyq.replicator.common.commands;

@CommandFactoryManager.AutoLoad
public final class FetchSnapshotRequestCommand extends RequestCommand {
    private static final byte VERSION = 1;

    public FetchSnapshotRequestCommand() {
        super(VERSION);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FETCH_SNAPSHOT;
    }

    @Override
    public void serialize() {
    }

    @Override
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
        return "FetchSnapshotRequest{" +
                super.toString() +
                '}';
    }
}

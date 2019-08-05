package com.github.ylgrgyq.replicator.common.protocol.v1;

@CommandFactoryManager.AutoLoad
public final class FetchSnapshotRequestCommand extends RequestCommandV1 {
    public FetchSnapshotRequestCommand() {
        super(MessageType.FETCH_SNAPSHOT);
    }

    @Override
    public void serialize() {
    }

    @Override
    public void deserialize() {
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

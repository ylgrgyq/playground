package com.github.ylgrgyq.replicator.common.protocol.v1;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;
import com.github.ylgrgyq.replicator.proto.Snapshot;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;

@CommandFactoryManager.AutoLoad
public final class FetchSnapshotResponseCommand extends ResponseCommandV1 {
    private static final int MINIMUM_LENGTH = 4;

    private Snapshot snapshot;

    public FetchSnapshotResponseCommand() {
        super(MessageType.FETCH_SNAPSHOT);
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void serialize() {
        byte[] buffer;
        if (snapshot != null) {
            int size = snapshot.getSerializedSize();
            buffer = new byte[Integer.BYTES + size];
            Bits.putInt(buffer, 0, size);
            System.arraycopy(snapshot.toByteArray(), 0, buffer, 4, size);
        } else {
            buffer = new byte[Integer.BYTES];
            Bits.putInt(buffer, 0, 0);
        }
        setContent(buffer);
    }

    @Override
    public void deserialize() throws DeserializationException {
        byte[] content = getContent();
        if (content != null && content.length >= MINIMUM_LENGTH) {
            int size = Bits.getInt(content, 0);
            if (size + Integer.BYTES == content.length) {
                if (size != 0) {
                    byte[] bs = new byte[size];
                    System.arraycopy(content, 4, bs, 0, size);
                    try {
                        snapshot = Snapshot.parseFrom(bs);
                    } catch (InvalidProtocolBufferException ex) {
                        throw new DeserializationException();
                    }
                }
            } else {
                throw new DeserializationException("Snapshot underflow");
            }
        } else {
            throw new DeserializationException("Fetch snapshot request command buffer underflow");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FetchSnapshotResponseCommand that = (FetchSnapshotResponseCommand) o;
        return Objects.equals(snapshot, that.snapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), snapshot);
    }

    @Override
    public String toString() {
        return "FetchSnapshotResponse{" +
                super.toString() +
                "snapshot=" + snapshot +
                '}';
    }
}

package com.github.ylgrgyq.replicator.common.entity;

import com.github.ylgrgyq.replicator.common.Bits;
import com.github.ylgrgyq.replicator.common.exception.DeserializationException;

import java.util.Objects;

public class FetchSnapshotResponse {
    private static final int MINIMUM_LENGTH = 4;

    private Snapshot snapshot;

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    public byte[] serialize() {
        byte[] buffer;
        if (snapshot != null) {
            byte[] snapshotInBytes = snapshot.serialize();
            int size = snapshotInBytes.length;
            buffer = new byte[Integer.BYTES + size];
            Bits.putInt(buffer, 0, size);
            System.arraycopy(snapshotInBytes, 0, buffer, 4, size);
        } else {
            buffer = new byte[Integer.BYTES];
            Bits.putInt(buffer, 0, 0);
        }
        return buffer;
    }

    public void deserialize(byte[] content) throws DeserializationException {
        if (content != null && content.length >= MINIMUM_LENGTH) {
            int size = Bits.getInt(content, 0);
            if (size + Integer.BYTES == content.length) {
                if (size != 0) {
                    byte[] bs = new byte[size];
                    System.arraycopy(content, 4, bs, 0, size);
                    snapshot = new Snapshot();
                    snapshot.deserialize(bs);
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
        FetchSnapshotResponse that = (FetchSnapshotResponse) o;
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

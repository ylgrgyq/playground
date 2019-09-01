package com.github.ylgrgyq.reservoir.storage;

import java.nio.ByteBuffer;
import java.util.Objects;

final class IndexBlockHandle {
    static int blockHandleSize = Long.BYTES + Integer.BYTES;

    private final long offset;
    private final int size;

    IndexBlockHandle(final long offset, final int size) {
        this.offset = offset;
        this.size = size;
    }

    long getOffset() {
        return offset;
    }

    int getSize() {
        return size;
    }

    byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(blockHandleSize);
        buffer.putLong(offset);
        buffer.putInt(size);

        return buffer.array();
    }

    static IndexBlockHandle decode(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long offset =  buffer.getLong();
        int size = buffer.getInt();
        return new IndexBlockHandle(offset, size);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final IndexBlockHandle handle = (IndexBlockHandle) o;
        return getOffset() == handle.getOffset() &&
                getSize() == handle.getSize();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOffset(), getSize());
    }

    @Override
    public String toString() {
        return "IndexBlockHandle{" +
                "offset=" + offset +
                ", size=" + size +
                '}';
    }
}


package com.github.ylgrgyq.reservoir.storage;

import java.nio.ByteBuffer;

final class Footer {
    static int tableFooterSize = IndexBlockHandle.blockHandleSize + Long.BYTES;

    private final IndexBlockHandle indexBlockHandle;

    Footer(IndexBlockHandle indexBlockHandle) {
        this.indexBlockHandle = indexBlockHandle;
    }

    IndexBlockHandle getIndexBlockHandle() {
        return indexBlockHandle;
    }

    byte[] encode() {
        byte[] indexBlock = indexBlockHandle.encode();
        ByteBuffer buffer = ByteBuffer.allocate(indexBlock.length + Long.BYTES);
        buffer.put(indexBlock);
        buffer.putLong(Constant.kTableMagicNumber);

        return buffer.array();
    }

    static Footer decode(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        byte[] indexBlockHandleBytes = new byte[IndexBlockHandle.blockHandleSize];
        buffer.get(indexBlockHandleBytes);

        long magic = buffer.getLong();
        if (magic != Constant.kTableMagicNumber) {
            throw new IllegalStateException("found invalid sstable during checking magic number");
        }

        IndexBlockHandle indexBlockHandle = IndexBlockHandle.decode(indexBlockHandleBytes);
        return new Footer(indexBlockHandle);
    }
}

package com.github.ylgrgyq.reservoir.storage;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

final class TableBuilder {
    private final FileChannel fileChannel;
    private final BlockBuilder dataBlock;
    private final BlockBuilder indexBlock;
    @Nullable
    private IndexBlockHandle pendingIndexBlockHandle;
    private long lastKey;
    private long offset;
    private boolean isFinished;

    TableBuilder(FileChannel fileChannel) {
        this.fileChannel = fileChannel;
        this.dataBlock = new BlockBuilder();
        this.indexBlock = new BlockBuilder();
        this.lastKey = -1;
    }

    void add(long k, byte[] v) throws IOException {
        assert k > lastKey;
        assert v.length > 0;
        assert !isFinished;

        if (pendingIndexBlockHandle != null) {
            indexBlock.add(k, pendingIndexBlockHandle.encode());
            pendingIndexBlockHandle = null;
        }

        dataBlock.add(k, v);

        if (dataBlock.getCurrentEstimateBlockSize() >= Constant.kLogMaxBlockSize) {
            flushDataBlock();
        }

        lastKey = k;
    }

    long finishBuild() throws IOException {
        assert ! isFinished;
        assert offset > 0;

        isFinished = true;

        if (!dataBlock.isEmpty()) {
            flushDataBlock();
        }

        if (pendingIndexBlockHandle != null) {
            indexBlock.add(lastKey + 1, pendingIndexBlockHandle.encode());
            pendingIndexBlockHandle = null;
        }

        IndexBlockHandle indexBlockHandle = writeBlock(indexBlock);

        Footer footer = new Footer(indexBlockHandle);
        byte[] footerBytes = footer.encode();
        fileChannel.write(ByteBuffer.wrap(footerBytes));

        offset += footerBytes.length;

        return offset;
    }

    private void flushDataBlock() throws IOException {
        assert ! dataBlock.isEmpty();
        pendingIndexBlockHandle = writeBlock(dataBlock);
        fileChannel.force(true);
    }

    private IndexBlockHandle writeBlock(BlockBuilder block) throws IOException{
        final long checksum = block.writeBlock(fileChannel);
        final int blockSize = block.getBlockSize();
        final IndexBlockHandle handle = new IndexBlockHandle(offset, blockSize);

        // write trailer
        final ByteBuffer trailer = ByteBuffer.allocate(Constant.kBlockTrailerSize);
        trailer.putLong(checksum);
        trailer.flip();
        fileChannel.write(trailer);

        dataBlock.reset();
        offset += blockSize + Constant.kBlockTrailerSize;

        return handle;
    }
}

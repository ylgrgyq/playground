package com.github.ylgrgyq.reservoir.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.ylgrgyq.reservoir.ObjectWithId;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;

final class Table implements Iterable<ObjectWithId> {
    static Block readBlockFromChannel(FileChannel fileChannel, IndexBlockHandle handle) throws IOException {
        ByteBuffer content = ByteBuffer.allocate(handle.getSize());
        fileChannel.read(content, handle.getOffset());

        ByteBuffer trailer = ByteBuffer.allocate(Constant.kBlockTrailerSize);
        fileChannel.read(trailer);
        trailer.flip();

        long expectChecksum = trailer.getLong();
        CRC32 actualChecksum = new CRC32();
        actualChecksum.update(content.array());
        if (expectChecksum != actualChecksum.getValue()) {
            throw new IllegalArgumentException("actualChecksum: " + actualChecksum.getValue()
                    + " (expect: = " + expectChecksum + ")");
        }

        return new Block(content);
    }

    private final Cache<Long, Block> dataBlockCache = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(2048)
            .build();

    private final FileChannel fileChannel;
    private final Block indexBlock;

    private Table(FileChannel fileChannel, Block indexBlock) {
        this.fileChannel = fileChannel;
        this.indexBlock = indexBlock;
    }

    static Table open(FileChannel fileChannel, long fileSize) throws IOException {
        long footerOffset = fileSize - Footer.tableFooterSize;
        ByteBuffer footerBuffer = ByteBuffer.allocate(Footer.tableFooterSize);
        fileChannel.read(footerBuffer, footerOffset);
        footerBuffer.flip();
        Footer footer = Footer.decode(footerBuffer.array());

        IndexBlockHandle indexBlockHandle = footer.getIndexBlockHandle();
        Block indexBlock = readBlockFromChannel(fileChannel, indexBlockHandle);

        return new Table(fileChannel, indexBlock);
    }

    void close() throws IOException {
        fileChannel.close();
        dataBlockCache.invalidateAll();
    }

    private Block getBlock(IndexBlockHandle handle) throws IOException {
        Block block = dataBlockCache.getIfPresent(handle.getOffset());
        if (block == null) {
            block = readBlockFromChannel(fileChannel, handle);
            dataBlockCache.put(handle.getOffset(), block);
        }

        return block;
    }

    @Override
    public SeekableIterator<Long, ObjectWithId> iterator() {
        return new Itr(indexBlock);
    }

    private class Itr implements SeekableIterator<Long, ObjectWithId> {
        private final SeekableIterator<Long, ObjectWithId> indexBlockIter;
        @Nullable
        private SeekableIterator<Long, ObjectWithId> innerBlockIter;

        Itr(Block indexBlock) {
            this.indexBlockIter = indexBlock.iterator();
        }

        @Override
        public Itr seek(Long key) {
            indexBlockIter.seek(key);
            if (indexBlockIter.hasNext()) {
                innerBlockIter = createInnerBlockIter();
                innerBlockIter.seek(key);
            } else {
                innerBlockIter = null;
            }
            return this;
        }

        @Override
        public boolean hasNext() {
            if (innerBlockIter == null || !innerBlockIter.hasNext()) {
                if (indexBlockIter.hasNext()) {
                    innerBlockIter = createInnerBlockIter();
                }
            }

            return innerBlockIter != null && innerBlockIter.hasNext();
        }

        private SeekableIterator<Long, ObjectWithId> createInnerBlockIter() {
            try {
                ObjectWithId kv = indexBlockIter.next();
                IndexBlockHandle handle = IndexBlockHandle.decode(kv.getObjectInBytes());
                Block block = getBlock(handle);
                return block.iterator();
            } catch (IOException ex) {
                throw new StorageRuntimeException("create inner block iterator failed", ex);
            }
        }

        @Override
        public ObjectWithId next() {
            assert innerBlockIter != null;
            assert innerBlockIter.hasNext();
            return innerBlockIter.next();
        }

    }


}

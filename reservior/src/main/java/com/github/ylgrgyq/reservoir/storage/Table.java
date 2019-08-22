package com.github.ylgrgyq.reservoir.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.ylgrgyq.reservoir.ObjectWithId;
import com.github.ylgrgyq.reservoir.StorageException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Author: ylgrgyq
 * Date: 18/6/10
 */
class Table implements Iterable<ObjectWithId> {
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

        BlockHandle indexBlockHandle = footer.getIndexBlockHandle();
        Block indexBlock = readBlock(fileChannel, indexBlockHandle);

        return new Table(fileChannel, indexBlock);
    }

    void close() throws IOException {
        fileChannel.close();
        dataBlockCache.invalidateAll();
    }

    private static Block readBlock(FileChannel fileChannel, BlockHandle handle) throws IOException {
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
                    + " (expect: = )" + expectChecksum);
        }

        return new Block(content);
    }

    List<ObjectWithId> getEntries(long start, long end) {
        SeekableIterator<Long, ObjectWithId> itr = iterator();
        itr.seek(start);

        List<ObjectWithId> ret = new ArrayList<>();
        while (itr.hasNext()) {
            ObjectWithId v = itr.next();
            long k = v.getId();
            if (k >= start && k < end) {
                ret.add(v);
            } else {
                break;
            }
        }

        return ret;
    }

    private Block getBlock(BlockHandle handle) throws IOException {
        Block block = dataBlockCache.getIfPresent(handle.getOffset());
        if (block == null) {
            block = readBlock(fileChannel, handle);
            dataBlockCache.put(handle.getOffset(), block);
        }

        return block;
    }

    @Override
    public SeekableIterator<Long, ObjectWithId> iterator() {
        return new Itr(indexBlock);
    }

    /**
     * Use this to throw checked exceptions from iterator methods that do not declare that they throw
     * checked exceptions.
     */
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    static <T extends Throwable> T getSneakyThrowable(Throwable t) throws T {
        throw (T) t;
    }

    private class Itr implements SeekableIterator<Long, ObjectWithId> {
        private final SeekableIterator<Long, KeyValueEntry<Long, byte[]>> indexBlockIter;
        private SeekableIterator<Long, KeyValueEntry<Long, byte[]>> innerBlockIter;

        Itr(Block indexBlock) {
            this.indexBlockIter = indexBlock.iterator();
        }

        @Override
        public void seek(Long key) {
            indexBlockIter.seek(key);
            if (indexBlockIter.hasNext()) {
                innerBlockIter = createInnerBlockIter();
                innerBlockIter.seek(key);
            } else {
                innerBlockIter = null;
            }
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

        private SeekableIterator<Long, KeyValueEntry<Long, byte[]>> createInnerBlockIter() {
            try {
                KeyValueEntry<Long, byte[]> kv = indexBlockIter.next();
                BlockHandle handle = BlockHandle.decode(kv.getVal());
                Block block = getBlock(handle);
                return block.iterator();
            } catch (IOException ex) {
                throw getSneakyThrowable(new StorageException("create inner block iterator failed", ex));
            }
        }

        @Override
        public ObjectWithId next() {
            assert innerBlockIter != null;
            assert innerBlockIter.hasNext();
            KeyValueEntry<Long, byte[]> ret = innerBlockIter.next();
            return new ObjectWithId(ret.getKey(), ret.getVal());
        }

    }


}

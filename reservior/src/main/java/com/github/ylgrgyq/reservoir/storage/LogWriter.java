package com.github.ylgrgyq.reservoir.storage;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;

final class LogWriter implements Closeable {
    private final FileChannel workingFileChannel;
    private final ByteBuffer headerBuffer;
    private int blockOffset;

    LogWriter(FileChannel workingFileChannel) throws IOException {
        this(workingFileChannel, 0);
    }

    LogWriter(FileChannel workingFileChannel, long writePosotion) throws IOException {
        assert workingFileChannel != null;

        workingFileChannel.position(writePosotion);
        this.workingFileChannel = workingFileChannel;
        this.headerBuffer = ByteBuffer.allocate(Constant.kLogHeaderSize);
        this.blockOffset = 0;
    }

    void flush() throws IOException {
        workingFileChannel.force(true);
    }

    @Override
    public void close() throws IOException {
        workingFileChannel.close();
    }

    void append(byte[] data) throws IOException {
        assert data != null;
        assert data.length > 0;

        final ByteBuffer writeBuffer = ByteBuffer.wrap(data);
        int dataSizeRemain = writeBuffer.remaining();
        boolean begin = true;

        while (dataSizeRemain > 0) {
            final int blockLeft = Constant.kBlockSize - blockOffset;
            assert blockLeft >= 0;

            // we don't expect data.length == 0, so if blockLeft == kLogHeaderSize
            // we need to allocate another block also
            if (blockLeft <= Constant.kLogHeaderSize) {
                paddingBlock(blockLeft);
                blockOffset = 0;
            }

            // Invariant: never leave < kLogHeaderSize bytes in a block
            assert Constant.kBlockSize - blockOffset - Constant.kLogHeaderSize >= 0;

            final RecordType type;
            final int blockForDataAvailable = Constant.kBlockSize - blockOffset - Constant.kLogHeaderSize;
            final int fragmentSize = Math.min(blockForDataAvailable, dataSizeRemain);
            final boolean end = fragmentSize == dataSizeRemain;
            if (begin && end) {
                type = RecordType.kFullType;
            } else if (begin) {
                type = RecordType.kFirstType;
            } else if (end) {
                type = RecordType.kLastType;
            } else {
                type = RecordType.kMiddleType;
            }

            byte[] out = new byte[fragmentSize];
            writeBuffer.get(out);
            writeRecord(type, out);

            begin = false;
            dataSizeRemain -= fragmentSize;
        }
    }

    private void paddingBlock(int blockLeft) throws IOException {
        assert blockLeft >= 0 : "blockLeft: " + blockLeft;

        if (blockLeft > 0) {
            // padding with bytes array full of zero
            ByteBuffer buffer = ByteBuffer.allocate(blockLeft);
            workingFileChannel.write(buffer);
        }
    }

    private void writeRecord(RecordType type, byte[] blockPayload) throws IOException {
        assert blockOffset + Constant.kLogHeaderSize + blockPayload.length <= Constant.kBlockSize;

        // format header
        headerBuffer.clear();
        // checksum includes the record type and record payload
        final CRC32 checksum = new CRC32();
        checksum.update(type.getCode());
        checksum.update(blockPayload);
        headerBuffer.putLong(checksum.getValue());
        headerBuffer.putShort((short) blockPayload.length);
        headerBuffer.put(type.getCode());
        headerBuffer.flip();

        // write hader and payload
        workingFileChannel.write(headerBuffer);
        workingFileChannel.write(ByteBuffer.wrap(blockPayload));
        blockOffset += blockPayload.length + Constant.kLogHeaderSize;
    }
}

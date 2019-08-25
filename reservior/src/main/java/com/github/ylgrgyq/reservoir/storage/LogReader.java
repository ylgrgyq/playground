package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

final class LogReader implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(LogReader.class);
    private static final ByteBuffer emptyBuffer = ByteBuffer.wrap(new byte[0]);
    private final FileChannel workingFileChannel;
    private final long initialOffset;
    private final boolean checkChecksum;
    private ByteBuffer buffer;
    private boolean eof;

    LogReader(FileChannel workingFileChannel, long initialOffset, boolean checkChecksum) {
        this.workingFileChannel = workingFileChannel;
        this.initialOffset = initialOffset;
        this.buffer = emptyBuffer;
        this.checkChecksum = checkChecksum;
    }

    List<byte[]> readLog() throws IOException, StorageException {
        if (initialOffset > 0) {
            skipToInitBlock();
        }

        boolean isFragmented = false;
        final ArrayList<byte[]> outPut = new ArrayList<>();
        while (true) {
            final RecordType type = readRecord(outPut);
            switch (type) {
                case kFullType:
                    if (isFragmented) {
                        throw new StorageException("partial record without end(1)");
                    }
                    return outPut;
                case kFirstType:
                    if (isFragmented) {
                        throw new StorageException("partial record without end(2)");
                    }
                    isFragmented = true;
                    break;
                case kMiddleType:
                    if (!isFragmented) {
                        throw new StorageException("missing start of fragmented record");
                    }
                    break;
                case kLastType:
                    if (!isFragmented) {
                        throw new StorageException("missing start for last fragmented record");
                    }
                    return outPut;
                case kCorruptedRecord:
                    buffer = emptyBuffer;
                    throw new BadRecordException(type);
                case kEOF:
                    buffer = emptyBuffer;
                    return Collections.emptyList();
                default:
                    throw new StorageException("unknown record type: " + type);
            }
        }
    }

    @Override
    public void close() throws IOException {
        workingFileChannel.close();
        buffer = emptyBuffer;
    }

    private void skipToInitBlock() throws IOException {
        long offsetInBlock = initialOffset % Constant.kBlockSize;
        long blockStartPosition = initialOffset - offsetInBlock;

        // if remaining space in block can not write a whole header, log writer
        // will write empty buffer to pad that space. so we should check if
        // offsetInBlock is within padding area and forward blockStartPosition
        // to the start position of the next real block
        if (offsetInBlock > Constant.kBlockSize - Constant.kHeaderSize + 1) {
            blockStartPosition += Constant.kBlockSize;
        }

        if (blockStartPosition > 0) {
            workingFileChannel.position(blockStartPosition);
        }
    }

    private RecordType readRecord(List<byte[]> out) throws IOException {
        // we don't expect empty data in log, so when remaining buffer is <= kHeaderSize
        // which means all of the bytes left in buffer is padding
        outer:
        while (buffer.remaining() <= Constant.kHeaderSize) {
            if (eof) {
                // encounter a truncated header at the end of the file. This can be caused
                // by writer crashing in the middle of writing the header. We condsider
                // this is OK and only report kEOF
                return RecordType.kEOF;
            } else {
                buffer = ByteBuffer.allocate(Constant.kBlockSize);
                while (buffer.hasRemaining()) {
                    int readBs = workingFileChannel.read(buffer);
                    if (readBs == -1) {
                        eof = true;
                        buffer.flip();
                        continue outer;
                    }
                }
                buffer.flip();
                break;
            }
        }

        // read header
        assert buffer.remaining() > Constant.kHeaderSize;
        final long expectChecksum = buffer.getLong();
        final short length = buffer.getShort();
        final byte typeCode = buffer.get();

        if (length > buffer.remaining()) {
            // if eof, this means writer crashing at the middle of writing the payload
            // we consider this is OK and return kEOF
            return eof ? RecordType.kEOF : RecordType.kCorruptedRecord;
        }


        final RecordType type = RecordType.getRecordTypeByCode(typeCode);
        if (type == null) {
            logger.debug("Got corrupted record with unknown record type code: {}", typeCode);
            return RecordType.kCorruptedRecord;
        }

        final byte[] buf = new byte[length];
        buffer.get(buf);
        if (checkChecksum) {
            final CRC32 actualChecksum = new CRC32();
            actualChecksum.update(typeCode);
            actualChecksum.update(buf);
            if (actualChecksum.getValue() != expectChecksum) {
                return RecordType.kCorruptedRecord;
            }
        }

        out.add(buf);
        return type;
    }
}

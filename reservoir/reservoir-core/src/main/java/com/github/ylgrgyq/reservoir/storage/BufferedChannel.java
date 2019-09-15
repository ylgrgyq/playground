package com.github.ylgrgyq.reservoir.storage;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

public class BufferedChannel implements AutoCloseable {
    private final FileChannel fileChannel;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private long readBufferStartPosition;

    public BufferedChannel(FileChannel fileChannel) {
        Objects.requireNonNull(fileChannel, "fileChannel");

        this.fileChannel = fileChannel;
        this.readBuffer = ByteBuffer.allocateDirect(Constant.kMaxDataBlockSize);
        this.readBuffer.flip();
        this.writeBuffer = ByteBuffer.allocateDirect(Constant.kMaxDataBlockSize);
        this.readBufferStartPosition = 0;
    }

    public BufferedChannel(FileChannel fileChannel, int readCapacity, int writeCapacity) {
        Objects.requireNonNull(fileChannel, "fileChannel");

        this.fileChannel = fileChannel;
        this.readBuffer = ByteBuffer.allocateDirect(readCapacity);
        this.writeBuffer = ByteBuffer.allocateDirect(writeCapacity);
    }

    public synchronized void write(ByteBuffer src) throws IOException {
        while (src.hasRemaining()) {
            writeBuffer.put(src);

            // flush when buffer is full
            if (!writeBuffer.hasRemaining()) {
                flush();
            }
        }
    }

    public synchronized void flush() throws IOException {
        writeBuffer.flip();
        do {
            fileChannel.write(writeBuffer);
        } while (writeBuffer.hasRemaining());

        writeBuffer.clear();
    }

    public void force(boolean forceMetadata) throws IOException {
        fileChannel.force(forceMetadata);
    }

    public int read(ByteBuffer dest) throws IOException {
        return read(dest, readBufferStartPosition + readBuffer.position());
    }

    public int read(ByteBuffer dest, long pos) throws IOException {
        return read(dest, pos, dest.limit());
    }

    public synchronized int read(ByteBuffer dest, long pos, int length) throws IOException {
        long currentPos = pos;
        final long eof = fileChannel.size();
        if (currentPos >= eof) {
            return -1;
        }

        while (length > 0) {
            if (readBufferStartPosition <= currentPos &&
                    currentPos < readBufferStartPosition + readBuffer.limit()) {
                final int startPosInBuffer = (int) (currentPos - readBufferStartPosition);
                readBuffer.position(startPosInBuffer);
                final int bytesSize = Math.min(length, readBuffer.remaining());

                final Buffer buf = readBuffer.slice().limit(bytesSize);
                dest.put((ByteBuffer)buf);
                readBuffer.position(startPosInBuffer + bytesSize);
                currentPos += bytesSize;
                length -= bytesSize;
            } else if (currentPos >= eof) {
                break;
            } else {
                readBufferStartPosition = currentPos;
                readBuffer.clear();
                if ((fileChannel.read(readBuffer, currentPos)) <= 0) {
                    throw new IOException("read from file returned non-positive value. Short read");
                }

                readBuffer.flip();
            }
        }

        return (int) (currentPos - pos);
    }

    @Override
    public synchronized void close() throws Exception {
        fileChannel.close();
        writeBuffer.clear();
        readBuffer.clear();
    }
}

package com.github.ylgrgyq.reservoir.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

public class BufferedReadChannel {
    protected final FileChannel fileChannel;
    private final ByteBuffer readBuffer;
    private long readBufferStartPosition;

    public BufferedReadChannel(FileChannel fileChannel, int readCapacity) {
        Objects.requireNonNull(fileChannel, "fileChannel");

        this.fileChannel = fileChannel;
        this.readBuffer = ByteBuffer.allocateDirect(readCapacity);
        this.readBufferStartPosition = Integer.MIN_VALUE;
    }

    public int read(ByteBuffer dest) throws IOException {
        return read(dest, readBufferStartPosition + readBuffer.position());
    }

    public int read(ByteBuffer dest, long pos) throws IOException {
        return read(dest, pos, dest.limit());
    }

    public synchronized int read(ByteBuffer dest, long pos, int length) throws IOException {
        long currentPos = pos;
        long eof = fileChannel.size();
        if (currentPos >= eof) {
            return -1;
        }

        while (length > 0) {
            if (readBufferStartPosition <= currentPos &&
                    currentPos < readBufferStartPosition + readBuffer.limit()) {
                int startPosInBuffer = (int) (currentPos - readBufferStartPosition);
                readBuffer.position(startPosInBuffer);
                int bytesSize = Math.min(length, readBuffer.remaining());

                dest.put(readBuffer);
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
}

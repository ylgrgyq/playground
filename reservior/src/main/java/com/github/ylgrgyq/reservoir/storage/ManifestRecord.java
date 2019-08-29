package com.github.ylgrgyq.reservoir.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: ylgrgyq
 * Date: 18/6/10
 */
class ManifestRecord {
    private static int SSTABLE_META_INFO_ENCODE_SIZE = Integer.BYTES + Long.BYTES * 3;

    private int nextFileNumber;
    private int logNumber;
    private Type type;
    private final List<SSTableFileMetaInfo> metas;

    private ManifestRecord(Type type) {
        this.metas = new ArrayList<>();
        this.type = type;
    }

    static ManifestRecord newPlainRecord() {
        return new ManifestRecord(Type.PLAIN);
    }

    static ManifestRecord newReplaceAllExistedMetasRecord() {
        return new ManifestRecord(Type.REPLACE_METAS);
    }

    int getNextFileNumber() {
        if (type == Type.PLAIN) {
            return nextFileNumber;
        } else {
            return -1;
        }
    }

    void setNextFileNumber(int nextFileNumber) {
        assert type == Type.PLAIN;
        this.nextFileNumber = nextFileNumber;
    }

    int getLogNumber() {
        if (type == Type.PLAIN) {
            return logNumber;
        } else {
            return -1;
        }
    }

    void setLogNumber(int logNumber) {
        this.logNumber = logNumber;
    }

    Type getType() {
        return type;
    }

    List<SSTableFileMetaInfo> getMetas() {
        return metas;
    }

    void addMeta(SSTableFileMetaInfo meta) {
        assert meta != null;
        metas.add(meta);
    }

    void addMetas(List<SSTableFileMetaInfo> ms) {
        assert ms != null && !ms.isEmpty();
        metas.addAll(ms);
    }

    byte[] encode() {
        final ByteBuffer buffer = ByteBuffer.allocate(metas.size() * SSTABLE_META_INFO_ENCODE_SIZE + Integer.BYTES * 3);
        buffer.putInt(nextFileNumber);
        buffer.putInt(logNumber);
        buffer.putInt(metas.size());

        for (SSTableFileMetaInfo meta : metas) {
            buffer.putLong(meta.getFileSize());
            buffer.putInt(meta.getFileNumber());
            buffer.putLong(meta.getFirstKey());
            buffer.putLong(meta.getLastKey());
        }

        return buffer.array();
    }

    private static byte[] compact(List<byte[]> output) {
        final int size = output.stream().mapToInt(b -> b.length).sum();
        final ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] bytes : output) {
            buffer.put(bytes);
        }
        return buffer.array();
    }

    static ManifestRecord decode(List<byte[]> bytes) {
        final ManifestRecord record = new ManifestRecord(Type.PLAIN);

        final ByteBuffer buffer = ByteBuffer.wrap(compact(bytes));
        record.setNextFileNumber(buffer.getInt());
        record.setLogNumber(buffer.getInt());

        final int metasSize = buffer.getInt();
        for (int i = 0; i < metasSize; i++) {
            SSTableFileMetaInfo meta = new SSTableFileMetaInfo();
            meta.setFileSize(buffer.getLong());
            meta.setFileNumber(buffer.getInt());
            meta.setFirstKey(buffer.getLong());
            meta.setLastKey(buffer.getLong());

            record.addMeta(meta);
        }

        return record;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("ManifestRecord{" +
                "type=" + type +
                ", nextFileNumber=" + nextFileNumber +
                ", logNumber=" + logNumber);

        if (!metas.isEmpty()) {
            long from = metas.get(0).getFirstKey();
            long to = metas.get(metas.size() - 1).getLastKey();
            builder.append(", metaKeysFrom=");
            builder.append(from);
            builder.append(", metaKeysTo=");
            builder.append(to);
        }

        builder.append("}");

        return builder.toString();
    }

    enum Type {
        PLAIN,
        REPLACE_METAS
    }
}

package com.github.ylgrgyq.reservoir.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ManifestRecord {
    private final List<SSTableFileMetaInfo> metas;
    private int nextFileNumber;
    private int dataLogFileNumber;

    ManifestRecord() {
        this.metas = new ArrayList<>();
    }

    int getNextFileNumber() {
        return nextFileNumber;
    }

    void setNextFileNumber(int nextFileNumber) {
        this.nextFileNumber = nextFileNumber;
    }

    int getDataLogFileNumber() {
        return dataLogFileNumber;
    }

    void setDataLogFileNumber(int number) {
        this.dataLogFileNumber = number;
    }

    List<SSTableFileMetaInfo> getMetas() {
        return metas;
    }

    void addMeta(SSTableFileMetaInfo meta) {
        metas.add(meta);
    }

    void addMetas(List<SSTableFileMetaInfo> ms) {
        metas.addAll(ms);
    }

    byte[] encode() {
        final int sstableMetaInfoEncodeSize = Integer.BYTES + Long.BYTES * 3;
        final ByteBuffer buffer = ByteBuffer.allocate(metas.size() * sstableMetaInfoEncodeSize + Integer.BYTES * 3);
        buffer.putInt(nextFileNumber);
        buffer.putInt(dataLogFileNumber);
        buffer.putInt(metas.size());

        for (SSTableFileMetaInfo meta : metas) {
            buffer.putLong(meta.getFileSize());
            buffer.putInt(meta.getFileNumber());
            buffer.putLong(meta.getFirstId());
            buffer.putLong(meta.getLastId());
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
        final ManifestRecord record = new ManifestRecord();

        final ByteBuffer buffer = ByteBuffer.wrap(compact(bytes));
        record.setNextFileNumber(buffer.getInt());
        record.setDataLogFileNumber(buffer.getInt());

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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ManifestRecord that = (ManifestRecord) o;
        return getNextFileNumber() == that.getNextFileNumber() &&
                getDataLogFileNumber() == that.getDataLogFileNumber() &&
                getMetas().equals(that.getMetas());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNextFileNumber(), getDataLogFileNumber(), getMetas());
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("ManifestRecord{" +
                ", nextFileNumber=" + nextFileNumber +
                ", dataLogFileNumber=" + dataLogFileNumber);

        if (!metas.isEmpty()) {
            long from = metas.get(0).getFirstId();
            long to = metas.get(metas.size() - 1).getLastId();
            builder.append(", metaKeysFrom=");
            builder.append(from);
            builder.append(", metaKeysTo=");
            builder.append(to);
        }

        builder.append("}");

        return builder.toString();
    }
}

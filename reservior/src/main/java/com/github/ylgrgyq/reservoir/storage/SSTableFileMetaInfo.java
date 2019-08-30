package com.github.ylgrgyq.reservoir.storage;

import java.util.Objects;

class SSTableFileMetaInfo {
    private int fileNumber;
    private long firstKey;
    private long lastKey;
    private long fileSize;

    void setFileNumber(int fileNumber) {
        this.fileNumber = fileNumber;
    }

    void setFirstKey(long firstIndex) {
        this.firstKey = firstIndex;
    }

    void setLastKey(long lastIndex) {
        this.lastKey = lastIndex;
    }

    void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    int getFileNumber() {
        return fileNumber;
    }

    long getFirstId() {
        return firstKey;
    }

    long getLastId() {
        return lastKey;
    }

    long getFileSize() {
        return fileSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SSTableFileMetaInfo)) return false;
        SSTableFileMetaInfo that = (SSTableFileMetaInfo) o;
        return getFileNumber() == that.getFileNumber() &&
                getFirstId() == that.getFirstId() &&
                getLastId() == that.getLastId() &&
                getFileSize() == that.getFileSize();
    }

    @Override
    public int hashCode() {

        return Objects.hash(getFileNumber(), getFirstId(), getLastId(), getFileSize());
    }
}

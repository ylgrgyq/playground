package com.github.ylgrgyq.server.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemoryStorage implements Storage {
    private List<byte[]> datas;
    private long offsetIndex;
    private Lock readLock;
    private Lock writeLock;

    public MemoryStorage(String topic) {
        this.datas = new ArrayList<>();
        this.offsetIndex = -1;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
    }

    @Override
    public void init() {

    }

    @Override
    public long getFirstIndex() {
        readLock.lock();
        try {
            if (datas.isEmpty()) {
                return -1;
            }

            return offsetIndex;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long getLastIndex() {
        readLock.lock();
        try {
            if (datas.isEmpty()) {
                return -1;
            }

            return offsetIndex + datas.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long pendingLogSize() {
        readLock.lock();
        try {
            return datas.stream().mapToInt(d -> d.length).sum();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void append(byte[] data) {
        writeLock.lock();
        try {
            datas.add(data);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public List<byte[]> getEntries(long fromIndex, int limit) {
        readLock.lock();
        try {
            int index = (int) (fromIndex - offsetIndex);
            if (index < 0) {
                return Collections.emptyList();
            }

            if (index > datas.size()) {
                return Collections.emptyList();
            }

            return new ArrayList<>(datas.subList(index, Math.min(index + limit, datas.size())));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void trimToIndex(long toIndex) {
        writeLock.lock();
        try {
            long index = toIndex;
            if (index < offsetIndex) {
                return;
            }

            if (index > offsetIndex + datas.size()) {
                datas = new ArrayList<>();
            }

            index = index - offsetIndex;
            datas = new ArrayList<>(datas.subList((int) index, datas.size()));
        } finally {
            writeLock.unlock();
        }
    }
}

package com.github.ylgrgyq.server.storage;

import com.github.ylgrgyq.proto.LogEntry;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemoryStorage implements Storage {
    private List<LogEntry> datas;
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
            return offsetIndex;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long getLastIndex() {
        readLock.lock();
        try {
            return offsetIndex + datas.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long pendingLogSize() {
        readLock.lock();
        try {
            return datas.stream().mapToInt(d -> d.getData().size()).sum();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void append(byte[] data) {
        writeLock.lock();
        try {
            LogEntry.Builder builder = LogEntry.newBuilder();
            builder.setData(ByteString.copyFrom(data));
            builder.setIndex(offsetIndex + datas.size());
            datas.add(builder.build());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public List<LogEntry> getEntries(long fromIndex, int limit) {
        readLock.lock();
        try {
            int index = (int) (fromIndex - offsetIndex);
            if (index < 0) {
                throw new LogsCompactedException(fromIndex);
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
            int index = (int) (toIndex - offsetIndex);

            if (index < 0) {
                return;
            }

            if (index > datas.size()) {
                datas = new ArrayList<>();
            }

            datas = new ArrayList<>(datas.subList(index, datas.size()));
        } finally {
            writeLock.unlock();
        }
    }
}

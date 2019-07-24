package com.github.ylgrgyq.replicator.server.storage;

import com.github.ylgrgyq.replicator.proto.LogEntry;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemoryStorage implements Storage {
    private static final Logger logger = LoggerFactory.getLogger(MemoryStorage.class);

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
    public long getFirstLogId() {
        readLock.lock();
        try {
            if (datas.isEmpty()) {
                return offsetIndex;
            } else {
                return offsetIndex + 1;
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long getLastLogId() {
        readLock.lock();
        try {
            return offsetIndex + datas.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void append(long id, byte[] data) {
        writeLock.lock();
        try {
            logger.info("append {} {}", id, data);
            LogEntry.Builder builder = LogEntry.newBuilder();
            builder.setData(ByteString.copyFrom(data));
            builder.setId(id);
            builder.setIndex(offsetIndex + datas.size() + 1);
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

            if (index >= datas.size()) {
                return Collections.emptyList();
            }

            return new ArrayList<>(datas.subList(index, Math.min(index + limit, datas.size())));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void trimToId(long toId) {
        writeLock.lock();
        try {
            logger.info("trim {}", toId);
            if (datas.isEmpty()) {
                return;
            }

            int maxIndex = Integer.MAX_VALUE;
            for (int i = 0; i < datas.size(); ++i) {
                LogEntry entry = datas.get(i);
                if (entry.getId() > toId) {
                    maxIndex = i;
                    break;
                }
            }

            if (maxIndex >= datas.size()) {
                offsetIndex = datas.get(datas.size() - 1).getIndex();
                datas = new ArrayList<>();

            } else {
                datas = new ArrayList<>(datas.subList(maxIndex, datas.size()));
                offsetIndex = datas.get(0).getIndex() - 1;
            }


        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void shutdown() {

    }
}

package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.NamedThreadFactory;
import com.github.ylgrgyq.reservoir.StorageException;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class FileStorageBuilder {
    public static FileStorageBuilder newBuilder(String storageBaseDir) {
        requireNonNull(storageBaseDir, "storageBaseDir");

        return new FileStorageBuilder(storageBaseDir);
    }

    private boolean forceSyncOnFlushConsumerCommitLogWriter = false;
    private boolean forceSyncOnFlushDataLogWriter = false;
    private long readRetryIntervalMillis = 500;
    private long truncateIntervalMillis = TimeUnit.MINUTES.toMillis(1);
    private final String storageBaseDir;

    @Nullable
    private ExecutorService flushMemtableExecutorService;

    private FileStorageBuilder(final String storageBaseDir) {
        this.storageBaseDir = storageBaseDir;
    }

    String getStorageBaseDir() {
        return storageBaseDir;
    }

    long getReadRetryIntervalMillis() {
        return readRetryIntervalMillis;
    }

    public FileStorageBuilder setReadRetryIntervalMillis(long readRetryInterval, TimeUnit unit) {
        if (readRetryInterval <= 0) {
            throw new IllegalArgumentException("readRetryInterval: " + readRetryInterval + " (expect: > 0)");
        }
        requireNonNull(unit, "unit");

        readRetryIntervalMillis = unit.toMillis(readRetryInterval);
        return this;
    }

    long getTruncateIntervalMillis() {
        return truncateIntervalMillis;
    }

    public FileStorageBuilder setTruncateIntervalMillis(long truncateInterval, TimeUnit unit) {
        if (truncateInterval < 0) {
            throw new IllegalArgumentException("truncateInterval: " + truncateInterval + " (expect: >= 0)");
        }
        requireNonNull(unit, "unit");

        truncateIntervalMillis = unit.toMillis(truncateInterval);
        return this;
    }

    ExecutorService getFlushMemtableExecutorService() {
        if (flushMemtableExecutorService == null) {
            flushMemtableExecutorService = Executors.newSingleThreadScheduledExecutor(
                    new NamedThreadFactory("memtable-writer-"));
        }
        return flushMemtableExecutorService;
    }

    public FileStorageBuilder setFlushMemtableExecutorService(ExecutorService flushMemtableExecutorService) {
        requireNonNull(flushMemtableExecutorService, "flushMemtableExecutorService");

        this.flushMemtableExecutorService = flushMemtableExecutorService;
        return this;
    }

    boolean isForceSyncOnFlushConsumerCommitLogWriter() {
        return forceSyncOnFlushConsumerCommitLogWriter;
    }

    public void setForceSyncOnFlushConsumerCommitLogWriter(boolean forceSyncOnFlushConsumerCommitLogWriter) {
        this.forceSyncOnFlushConsumerCommitLogWriter = forceSyncOnFlushConsumerCommitLogWriter;
    }

    boolean isForceSyncOnFlushDataLogWriter() {
        return forceSyncOnFlushDataLogWriter;
    }

    public void setForceSyncOnFlushDataLogWriter(boolean forceSyncOnFlushDataLogWriter) {
        this.forceSyncOnFlushDataLogWriter = forceSyncOnFlushDataLogWriter;
    }

    public FileStorage build() throws StorageException {
        if (flushMemtableExecutorService == null) {
            flushMemtableExecutorService = Executors.newSingleThreadScheduledExecutor(
                    new NamedThreadFactory("memtable-writer-"));
        }
        return new FileStorage(this);
    }


}

package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static java.util.Objects.requireNonNull;

public final class ObjectQueueBuilder<E> {
    private static final ThreadFactory threadFactory = new NamedThreadFactory("object-queue-executor-");

    public static <E> ObjectQueueBuilder<E> newBuilder() {
        return new ObjectQueueBuilder<>();
    }

    private ExecutorService executorService = Executors.newSingleThreadExecutor(threadFactory);
    private int ringBufferSize = 512;
    private int batchSize = 128;
    private boolean autoCommit = true;

    @Nullable
    private ObjectQueueStorage storage;
    @Nullable
    private Codec<E> codec;

    private ObjectQueueBuilder() {}

    ObjectQueueStorage getStorage() {
        assert storage != null;
        return storage;
    }

    public ObjectQueueBuilder<E> setStorage(ObjectQueueStorage storage) {
        requireNonNull(storage, "storage");
        this.storage = storage;
        return this;
    }

    Codec<E> getCodec() {
        assert codec != null;
        return codec;
    }

    public ObjectQueueBuilder<E> setCodec(@Nullable Codec<E> codec) {
        requireNonNull(codec, "codec");
        this.codec = codec;
        return this;
    }

    boolean isAutoCommit() {
        return autoCommit;
    }

    public ObjectQueueBuilder<E> setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
        return this;
    }

    int getBatchSize() {
        return batchSize;
    }

    public ObjectQueueBuilder<E> setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize: " + batchSize + " (expected: > 0)");
        }

        this.batchSize = batchSize;
        return this;
    }

    int getRingBufferSize() {
        return ringBufferSize;
    }

    public ObjectQueueBuilder<E> setRingBufferSize(int ringBufferSize) {
        if (ringBufferSize <= 0) {
            throw new IllegalArgumentException("ringBufferSize: " + ringBufferSize + " (expected: > 0)");
        }

        this.ringBufferSize = ringBufferSize;
        return this;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        requireNonNull(executorService, "executorService");

        this.executorService = executorService;
    }

    public ObjectQueueProducer<E> buildProducer() throws StorageException {
        requireNonNull(storage, "storage");
        requireNonNull(codec, "serializer");
        requireNonNull(executorService, "executorService");

        return new DisruptorBackedObjectQueueProducer<>(this);
    }

    public ObjectQueueConsumer<E> buildConsumer() throws StorageException {
        requireNonNull(storage, "storage");
        requireNonNull(codec, "deserializer");

        if (autoCommit) {
            return new AutoCommitObjectQueueConsumer<>(this);
        } else {
            return new ManualCommitObjectQueueConsumer<>(this);
        }
    }

    public ObjectQueue<E> buildQueue() throws StorageException {
        ObjectQueueProducer<E> producer = buildProducer();
        ObjectQueueConsumer<E> consumer = buildConsumer();

        return new ObjectQueue<>(producer, consumer);
    }
}

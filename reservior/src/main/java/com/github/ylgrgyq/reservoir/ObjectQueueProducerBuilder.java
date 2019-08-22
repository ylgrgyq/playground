package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static java.util.Objects.requireNonNull;

public final class ObjectQueueProducerBuilder<E> {
    private static final ThreadFactory threadFactory = new NamedThreadFactory("object-queue-producer-executor-");

    public static <E> ObjectQueueProducerBuilder<E> newBuilder() {
        return new ObjectQueueProducerBuilder<>();
    }

    private ExecutorService executorService = Executors.newSingleThreadExecutor(threadFactory);
    private int ringBufferSize = 512;
    private int batchSize = 128;

    @Nullable
    private ObjectQueueStorage storage;
    @Nullable
    private Serializer<E> serializer;

    ObjectQueueStorage getStorage() {
        assert storage != null;
        return storage;
    }

    public ObjectQueueProducerBuilder<E> setStorage(ObjectQueueStorage storage) {
        requireNonNull(storage, "storage");

        this.storage = storage;
        return this;
    }

    Serializer<E> getSerializer() {
        assert serializer != null;
        return serializer;
    }

    int getRingBufferSize() {
        return ringBufferSize;
    }

    public ObjectQueueProducerBuilder<E> setRingBufferSize(int ringBufferSize) {
        if (ringBufferSize <= 0) {
            throw new IllegalArgumentException("ringBufferSize: " + ringBufferSize + " (expected: > 0)");
        }

        this.ringBufferSize = ringBufferSize;
        return this;
    }

    int getBatchSize() {
        return batchSize;
    }

    public ObjectQueueProducerBuilder<E> setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize: " + batchSize + " (expected: > 0)");
        }

        this.batchSize = batchSize;
        return this;
    }

    public ObjectQueueProducerBuilder<E> setSerializer(Serializer<E> serializer) {
        requireNonNull(serializer, "serializer");
        this.serializer = serializer;
        return this;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        requireNonNull(executorService, "executorService");

        this.executorService = executorService;
    }

    public DisruptorBackedObjectQueueProducer<E> build() throws StorageException {
        requireNonNull(storage, "storage");
        requireNonNull(serializer, "serializer");
        requireNonNull(executorService, "executorService");

        return new DisruptorBackedObjectQueueProducer<>(this);
    }
}

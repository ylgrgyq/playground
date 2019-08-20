package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Objects.requireNonNull;

public final class ObjectQueueProducerBuilder<E> {
    @Nullable
    private ProducerStorage storage;
    @Nullable
    private Serializer<E> serializer;
    private ExecutorService executorService = Executors.newSingleThreadExecutor(new NamedThreadFactory("object-queue-producer-executor-"));
    private int ringBufferSize = 512;
    private int batchSize = 128;


    public static <E> ObjectQueueProducerBuilder<E> newBuilder() {
        return new ObjectQueueProducerBuilder<>();
    }

    ProducerStorage getStorage() {
        assert storage != null;
        return storage;
    }

    public ObjectQueueProducerBuilder<E> setStorage(ProducerStorage storage) {
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
            throw new IllegalArgumentException("ringBufferSize should greater than zero, actual: " + ringBufferSize);
        }

        this.ringBufferSize = ringBufferSize;
        return this;
    }

    int getBatchSize() {
        return batchSize;
    }

    public ObjectQueueProducerBuilder<E> setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize should greater than zero, actual: " + batchSize);
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

    public ObjectQueueProducer<E> build() throws StorageException {
        requireNonNull(storage, "storage");
        requireNonNull(serializer, "serializer");
        requireNonNull(executorService, "executorService");

        return new ObjectQueueProducer<>(this);
    }
}

package com.github.ylgrgyq.resender;

import static java.util.Objects.requireNonNull;

public final class ObjectQueueProducerBuilder<E> {
    private ProducerStorage storage;
    private Serializer<E> serializer;
    private int ringBufferSize = 512;
    private int batchSize = 128;


    public static <E> ObjectQueueProducerBuilder<E> newBuilder() {
        return new ObjectQueueProducerBuilder<>();
    }

    ProducerStorage getStorage() {
        return storage;
    }

    public ObjectQueueProducerBuilder<E> setStorage(ProducerStorage storage) {
        requireNonNull(storage, "storage");

        this.storage = storage;
        return this;
    }

    Serializer<E> getSerializer() {
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

    public ObjectQueueProducer<E> build() {
        return new ObjectQueueProducer<>(this);
    }
}

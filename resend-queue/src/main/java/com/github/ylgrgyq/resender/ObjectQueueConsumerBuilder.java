package com.github.ylgrgyq.resender;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public final class ObjectQueueConsumerBuilder<E> {
    private boolean autoCommit = true;
    private int batchSize = 1024;

    @Nullable
    private ConsumerStorage storage;
    @Nullable
    private Deserializer<E> deserializer;

    private ObjectQueueConsumerBuilder() {}

    public static <E> ObjectQueueConsumerBuilder<E> newBuilder() {
        return new ObjectQueueConsumerBuilder<>();
    }

    ConsumerStorage getStorage() {
        assert storage != null;
        return storage;
    }

    public ObjectQueueConsumerBuilder<E> setStorage(ConsumerStorage storage) {
        requireNonNull(storage, "storage");
        this.storage = storage;
        return this;
    }


    Deserializer<E> getDeserializer() {
        assert deserializer != null;
        return deserializer;
    }

    public ObjectQueueConsumerBuilder<E> setDeserializer(Deserializer<E> deserializer) {
        requireNonNull(deserializer, "deserializer");
        this.deserializer = deserializer;
        return this;
    }

    boolean isAutoCommit() {
        return autoCommit;
    }

    public ObjectQueueConsumerBuilder<E> setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
        return this;
    }

    int getBatchSize() {
        return batchSize;
    }

    public ObjectQueueConsumerBuilder<E> setBatchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public ObjectQueueConsumer<E> build() {
        requireNonNull(storage, "storage");
        requireNonNull(deserializer, "deserializer");

        return new ObjectQueueConsumer<>(this);
    }
}

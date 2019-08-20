package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public final class ObjectQueueConsumerBuilder<E> {
    public static <E> ObjectQueueConsumerBuilder<E> newBuilder() {
        return new ObjectQueueConsumerBuilder<>();
    }

    private boolean autoCommit = true;
    private int batchSize = 1024;

    @Nullable
    private ConsumerStorage storage;
    @Nullable
    private Deserializer<E> deserializer;

    private ObjectQueueConsumerBuilder() {}

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
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize: " + batchSize + " (expected: > 0)");
        }

        this.batchSize = batchSize;
        return this;
    }

    public ObjectQueueConsumer<E> build() throws StorageException{
        requireNonNull(storage, "storage");
        requireNonNull(deserializer, "deserializer");

        if (autoCommit) {
            return new AutoCommitObjectQueueConsumer<>(this);
        } else {
            return new ManualCommitObjectQueueConsumer<>(this);
        }
    }
}

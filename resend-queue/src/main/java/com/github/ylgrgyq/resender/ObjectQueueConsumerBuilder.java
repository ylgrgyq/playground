package com.github.ylgrgyq.resender;

import static java.util.Objects.requireNonNull;

public final class ObjectQueueConsumerBuilder {
    private boolean autoCommit = true;
    private int batchSize = 1024;

    private ConsumerStorage storage;
    private Deserializer deserializer;

    private ObjectQueueConsumerBuilder() {}

    public static ObjectQueueConsumerBuilder newBuilder() {
        return new ObjectQueueConsumerBuilder();
    }

    ConsumerStorage getStorage() {
        return storage;
    }

    public ObjectQueueConsumerBuilder setStorage(ConsumerStorage storage) {
        requireNonNull(storage, "storage");
        this.storage = storage;
        return this;
    }

    @SuppressWarnings("unchecked")
    <E> Deserializer<E> getDeserializer() {
        return (Deserializer<E>)deserializer;
    }

    public ObjectQueueConsumerBuilder setDeserializer(Deserializer deserializer) {
        requireNonNull(deserializer, "deserializer");
        this.deserializer = deserializer;
        return this;
    }

    boolean isAutoCommit() {
        return autoCommit;
    }

    public ObjectQueueConsumerBuilder setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
        return this;
    }

    int getBatchSize() {
        return batchSize;
    }

    public ObjectQueueConsumerBuilder setBatchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public <E> ObjectQueueConsumer<E> build() {
        requireNonNull(storage, "storage");
        requireNonNull(deserializer, "deserializer");

        return new ObjectQueueConsumer<>(this);
    }
}

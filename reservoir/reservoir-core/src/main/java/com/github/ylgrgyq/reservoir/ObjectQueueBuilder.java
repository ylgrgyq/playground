package com.github.ylgrgyq.reservoir;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static java.util.Objects.requireNonNull;

public final class ObjectQueueBuilder<E, S> {
    private static final ThreadFactory threadFactory = new NamedThreadFactory("object-queue-executor-");

    public static <E, S> ObjectQueueBuilder<E, S> newBuilder(ObjectQueueStorage<S> storage, Codec<E, S> codec) {
        requireNonNull(storage, "storage");
        requireNonNull(codec, "codec");

        return new ObjectQueueBuilder<>(storage, codec);
    }

    private ExecutorService executorService = Executors.newSingleThreadExecutor(threadFactory);
    private int producerRingBufferSize = 512;
    private int consumerFetchBatchSize = 128;
    private boolean autoCommit = true;

    private ObjectQueueStorage<S> storage;
    private Codec<E, S> codec;

    private ObjectQueueBuilder(ObjectQueueStorage<S> storage, Codec<E, S> codec) {
        this.storage = storage;
        this.codec = codec;
    }


    public ObjectQueueBuilder<E, S> setStorage(ObjectQueueStorage<S> storage) {
        requireNonNull(storage, "storage");
        this.storage = storage;
        return this;
    }


    public ObjectQueueBuilder<E, S> setCodec(Codec<E, S> codec) {
        requireNonNull(codec, "codec");
        this.codec = codec;
        return this;
    }

    public ObjectQueueBuilder<E, S> setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
        return this;
    }


    public ObjectQueueBuilder<E, S> setConsumerFetchBatchSize(int consumerFetchBatchSize) {
        if (consumerFetchBatchSize <= 0) {
            throw new IllegalArgumentException("consumerFetchBatchSize: " + consumerFetchBatchSize + " (expected: > 0)");
        }

        this.consumerFetchBatchSize = consumerFetchBatchSize;
        return this;
    }


    public ObjectQueueBuilder<E, S> setProducerRingBufferSize(int producerRingBufferSize) {
        if (producerRingBufferSize <= 0) {
            throw new IllegalArgumentException("producerRingBufferSize: " + producerRingBufferSize + " (expected: > 0)");
        }

        this.producerRingBufferSize = producerRingBufferSize;
        return this;
    }

    public void setExecutorService(ExecutorService executorService) {
        requireNonNull(executorService, "executorService");

        this.executorService = executorService;
    }

    public ObjectQueueProducer<E> buildProducer() throws StorageException {
        return new DisruptorBackedObjectQueueProducer<>(this);
    }

    public ObjectQueueConsumer<E> buildConsumer() throws StorageException {
        if (isAutoCommit()) {
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

    int getProducerRingBufferSize() {
        return producerRingBufferSize;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    ObjectQueueStorage<S> getStorage() {
        return storage;
    }

    Codec<E, S> getCodec() {
        return codec;
    }

    int getConsumerFetchBatchSize() {
        return consumerFetchBatchSize;
    }

    private boolean isAutoCommit() {
        return autoCommit;
    }
}

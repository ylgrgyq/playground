package com.github.ylgrgyq.reservoir;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

public final class AutomaticObjectQueueConsumerBuilder<E extends Verifiable> {
    public static <E extends Verifiable> AutomaticObjectQueueConsumerBuilder<E> newBuilder() {
        return new AutomaticObjectQueueConsumerBuilder<>();
    }

    private final List<ConsumeObjectListener<E>> listeners;
    private final ObjectQueueBuilder<E> consumerBuilder;

    @Nullable
    private ConsumeObjectHandler<E> consumeObjectHandler;
    @Nullable
    private Executor listenerExecutor;
    @Nullable
    private ObjectQueueConsumer<E> consumer;

    private AutomaticObjectQueueConsumerBuilder() {
        this.consumerBuilder = ObjectQueueBuilder.newBuilder();
        this.listeners = new ArrayList<>();
    }

    ObjectQueueConsumer<E> getConsumer() {
        assert consumer != null;
        return consumer;
    }

    ObjectQueueStorage getStorage() {
        return consumerBuilder.getStorage();
    }

    public AutomaticObjectQueueConsumerBuilder<E> addConsumeElementListener(ConsumeObjectListener<E> listener) {
        requireNonNull(listener, "listener");
        listeners.add(listener);
        return this;
    }


    List<ConsumeObjectListener<E>> getConsumeElementListeners() {
        return listeners;
    }

    public AutomaticObjectQueueConsumerBuilder<E> setStorage(ObjectQueueStorage storage) {
        requireNonNull(storage, "storage");
        consumerBuilder.setStorage(storage);
        return this;
    }

    Codec<E> getCodec() {
        return consumerBuilder.getCodec();
    }

    public AutomaticObjectQueueConsumerBuilder<E> setCodec(Codec<E> codec) {
        requireNonNull(codec, "codec");
        consumerBuilder.setCodec(codec);
        return this;
    }

    boolean isAutoCommit() {
        return consumerBuilder.isAutoCommit();
    }

    public AutomaticObjectQueueConsumerBuilder<E> setAutoCommit(boolean autoCommit) {
        consumerBuilder.setAutoCommit(autoCommit);
        return this;
    }

    int getBatchSize() {
        return consumerBuilder.getConsumerFetchBatchSize();
    }

    public AutomaticObjectQueueConsumerBuilder<E> setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize: " + batchSize + " (expected: > 0)");
        }

        consumerBuilder.setConsumerFetchBatchSize(batchSize);
        return this;
    }

    ConsumeObjectHandler<E> getConsumeObjectHandler() {
        assert consumeObjectHandler != null;
        return consumeObjectHandler;
    }

    public AutomaticObjectQueueConsumerBuilder<E> setConsumeObjectHandler(ConsumeObjectHandler<E> consumeObjectHandler) {
        requireNonNull(consumeObjectHandler, "consumeObjectHandler");

        this.consumeObjectHandler = consumeObjectHandler;
        return this;
    }

    Executor getListenerExecutor() {
        assert listenerExecutor != null;
        return listenerExecutor;
    }

    public AutomaticObjectQueueConsumerBuilder<E> setListenerExecutor(Executor listenerExecutor) {
        requireNonNull(listenerExecutor, "listenerExecutor");

        this.listenerExecutor = listenerExecutor;
        return this;
    }

    public AutomaticObjectQueueConsumer<E> build() throws StorageException {
        requireNonNull(consumeObjectHandler, "consumeObjectHandler");
        requireNonNull(listenerExecutor, "listenerExecutor");

        consumer = consumerBuilder.buildConsumer();
        return new AutomaticObjectQueueConsumer<>(this);
    }
}

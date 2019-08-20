package com.github.ylgrgyq.resender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

public final class AutomaticObjectQueueConsumerBuilder<E extends Verifiable> {
    private final List<ConsumeObjectListener<E>> listeners;
    private final ObjectQueueConsumerBuilder<E> consumerBuilder;

    private ConsumeObjectHandler<E> consumeObjectHandler;
    private Executor listenerExecutor;
    private ObjectQueueConsumer<E> consumer;

    private AutomaticObjectQueueConsumerBuilder() {
        this.consumerBuilder = ObjectQueueConsumerBuilder.newBuilder();
        this.listeners = new ArrayList<>();
    }

    public static <E extends Verifiable> AutomaticObjectQueueConsumerBuilder<E> newBuilder() {
        return new AutomaticObjectQueueConsumerBuilder<>();
    }

    ObjectQueueConsumer<E> getConsumer() {
        return consumer;
    }

    ConsumerStorage getStorage() {
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

    public AutomaticObjectQueueConsumerBuilder<E> setStorage(ConsumerStorage storage) {
        requireNonNull(storage, "storage");
        consumerBuilder.setStorage(storage);
        return this;
    }

    Deserializer<E> getDeserializer() {
        return consumerBuilder.getDeserializer();
    }

    public AutomaticObjectQueueConsumerBuilder<E> setDeserializer(Deserializer<E> deserializer) {
        requireNonNull(deserializer, "deserializer");
        consumerBuilder.setDeserializer(deserializer);
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
        return consumerBuilder.getBatchSize();
    }

    public AutomaticObjectQueueConsumerBuilder<E> setBatchSize(int batchSize) {
        consumerBuilder.setBatchSize(batchSize);
        return this;
    }

    ConsumeObjectHandler<E> getConsumeObjectHandler() {
        return consumeObjectHandler;
    }

    public AutomaticObjectQueueConsumerBuilder<E> setConsumeObjectHandler(ConsumeObjectHandler<E> consumeObjectHandler) {
        this.consumeObjectHandler = consumeObjectHandler;
        return this;
    }

    Executor getListenerExecutor() {
        return listenerExecutor;
    }

    public AutomaticObjectQueueConsumerBuilder<E> setListenerExecutor(Executor listenerExecutor) {
        this.listenerExecutor = listenerExecutor;
        return this;
    }

    public AutomaticObjectQueueConsumer<E> build() {
        requireNonNull(consumeObjectHandler, "please provide consume object handler");
        requireNonNull(listenerExecutor, "please provide listener executor");

        this.consumer = consumerBuilder.build();

        return new AutomaticObjectQueueConsumer<>(this);
    }
}

package com.github.ylgrgyq.resender;

import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

public class AutomaticObjectQueueConsumerBuilder {
    private ObjectQueueConsumerBuilder consumerBuilder;
    private ConsumeObjectHandler handler;
    private Executor listenerExecutor;

    private AutomaticObjectQueueConsumerBuilder() {
        consumerBuilder = ObjectQueueConsumerBuilder.newBuilder();
    }

    public static AutomaticObjectQueueConsumerBuilder newBuilder() {
        return new AutomaticObjectQueueConsumerBuilder();
    }

    ConsumerStorage getStorage() {
        return consumerBuilder.getStorage();
    }

    public AutomaticObjectQueueConsumerBuilder setStorage(ConsumerStorage storage) {
        requireNonNull(storage, "storage");
        consumerBuilder.setStorage(storage);
        return this;
    }

    @SuppressWarnings("unchecked")
    <E> Deserializer<E> getDeserializer() {
        return consumerBuilder.getDeserializer();
    }

    public AutomaticObjectQueueConsumerBuilder setDeserializer(Deserializer deserializer) {
        requireNonNull(deserializer, "deserializer");
        consumerBuilder.setDeserializer(deserializer);
        return this;
    }

    boolean isAutoCommit() {
        return consumerBuilder.isAutoCommit();
    }

    public AutomaticObjectQueueConsumerBuilder setAutoCommit(boolean autoCommit) {
        consumerBuilder.setAutoCommit(autoCommit);
        return this;
    }

    int getBatchSize() {
        return consumerBuilder.getBatchSize();
    }

    public AutomaticObjectQueueConsumerBuilder setBatchSize(int batchSize) {
        consumerBuilder.setBatchSize(batchSize);
        return this;
    }

    @SuppressWarnings("unchecked")
    <E extends Payload> ConsumeObjectHandler<E> getHandler() {
        return (ConsumeObjectHandler<E>)handler;
    }

    public AutomaticObjectQueueConsumerBuilder setHandler(ConsumeObjectHandler handler) {
        this.handler = handler;
        return this;
    }

    Executor getListenerExecutor() {
        return listenerExecutor;
    }

    public AutomaticObjectQueueConsumerBuilder setListenerExecutor(Executor listenerExecutor) {
        this.listenerExecutor = listenerExecutor;
        return this;
    }

    public <E extends Payload> AutomaticObjectQueueConsumer<E> build() {
        requireNonNull(handler, "handler");
        requireNonNull(listenerExecutor, "listenerExecutor");

        ObjectQueueConsumer<E> consumer = consumerBuilder.build();
        return new AutomaticObjectQueueConsumer<>(consumer, this);
    }


}

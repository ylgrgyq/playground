package com.github.ylgrgyq.resender;

import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AutomaticObjectQueueConsumerTest {
    private final TestingStorage storage = new TestingStorage();

    @Before
    public void setUp() throws Exception {
        storage.clear();
    }

    @Test
    public void simpleConsume() throws Exception {
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();

        final AlwaysSuccessConsumeObjectHandler<TestingPayload> handler = new AlwaysSuccessConsumeObjectHandler<>();
        final AutomaticObjectQueueConsumer<TestingPayload> consumer = AutomaticObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(false)
                .setConsumeObjectHandler(handler)
                .addConsumeElementListener(new TestingConsumeObjectListener<>())
                .setListenerExecutor(Executors.newSingleThreadExecutor())
                .build();

        for (int i = 1; i < 64; i++) {
            TestingPayload payload = new TestingPayload(i, ("" + i).getBytes(StandardCharsets.UTF_8));
            storedPayload.add(payload.createPayloweWithId());
        }
        storage.store(storedPayload);

        await().until(() -> handler.getReceivedObjects().size() == storedPayload.size());
        assertThat(handler.getReceivedObjects().stream()
                .map(TestingPayload::createPayloweWithId)
                .collect(Collectors.toList()))
                .isEqualTo(storedPayload);
        consumer.close();
    }

    @Test
    public void consumeInvalidObject() throws Exception {
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();

        final AlwaysSuccessConsumeObjectHandler<TestingPayload> handler = new AlwaysSuccessConsumeObjectHandler<>();
        final TestingConsumeObjectListener<TestingPayload> listener = new TestingConsumeObjectListener<>();
        final AutomaticObjectQueueConsumer<TestingPayload> consumer = AutomaticObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(false)
                .setConsumeObjectHandler(handler)
                .addConsumeElementListener(listener)
                .setListenerExecutor(Executors.newSingleThreadExecutor())
                .build();

        for (int i = 1; i < 64; i++) {
            TestingPayload payload = new TestingPayload(i, ("" + i).getBytes(StandardCharsets.UTF_8)).setValid(false);
            storedPayload.add(payload.createPayloweWithId());
        }
        storage.store(storedPayload);

        await().until(() -> listener.getInvalidObjects().size() == storedPayload.size());
        assertThat(listener.getInvalidObjects().stream()
                .map(TestingPayload::createPayloweWithId)
                .collect(Collectors.toList()))
                .isEqualTo(storedPayload);
        consumer.close();
    }

    @Test
    public void shutdownAfterConsumeObjectFailed() {
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();

        final ShutdownAfterFailedHandler<TestingPayload> handler = new ShutdownAfterFailedHandler<>();
        final TestingConsumeObjectListener<TestingPayload> listener = new TestingConsumeObjectListener<>();
        final AutomaticObjectQueueConsumer<TestingPayload> consumer = AutomaticObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(false)
                .setConsumeObjectHandler(handler)
                .addConsumeElementListener(listener)
                .setListenerExecutor(Executors.newSingleThreadExecutor())
                .build();

        TestingPayload payload = new TestingPayload("Hello".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(payload.createPayloweWithId());

        storage.store(storedPayload);
        await().until(consumer::closed);
    }

    @Test
    public void retryAfterConsumeObjectFailed() {
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();

        final ShutdownAfterFailedHandler<TestingPayload> handler = new ShutdownAfterFailedHandler<>();
        final TestingConsumeObjectListener<TestingPayload> listener = new TestingConsumeObjectListener<>();
        final AutomaticObjectQueueConsumer<TestingPayload> consumer = AutomaticObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(false)
                .setConsumeObjectHandler(handler)
                .addConsumeElementListener(listener)
                .setListenerExecutor(Executors.newSingleThreadExecutor())
                .build();

        TestingPayload payload = new TestingPayload("Hello".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(payload.createPayloweWithId());

        storage.store(storedPayload);
        await().until(consumer::closed);
    }

    @Test
    public void ignoreAfterConsumeObjectFailed() {

    }

    @Test
    public void onHandleObjectFailedThrowsException() {
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();

        final AlwaysThrowsExceptionHandler<TestingPayload> handler = new AlwaysThrowsExceptionHandler<>();
        final TestingConsumeObjectListener<TestingPayload> listener = new TestingConsumeObjectListener<>();
        final AutomaticObjectQueueConsumer<TestingPayload> consumer = AutomaticObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(false)
                .setConsumeObjectHandler(handler)
                .addConsumeElementListener(listener)
                .setListenerExecutor(Executors.newSingleThreadExecutor())
                .build();

        TestingPayload payload = new TestingPayload("Hello".getBytes(StandardCharsets.UTF_8));
        storedPayload.add(payload.createPayloweWithId());

        storage.store(storedPayload);
        await().until(consumer::closed);
    }

    @Test
    public void onHandleObjectFailedReturnsNull() {

    }

    private static class AlwaysSuccessConsumeObjectHandler<E extends Verifiable> implements ConsumeObjectHandler<E> {
        private final List<E> receivedPayloads;

        public AlwaysSuccessConsumeObjectHandler() {
            this.receivedPayloads = new ArrayList<>();
        }

        public List<E> getReceivedObjects() {
            return receivedPayloads;
        }

        @Override
        public void onHandleObject(@Nonnull E obj) throws Exception {
            receivedPayloads.add(obj);
        }

        @Nonnull
        @Override
        public HandleFailedStrategy onHandleObjectFailed(@Nonnull E obj, @Nonnull Throwable throwable) {
            throw new IllegalStateException("can't be here");
        }
    }

    private static class AlwaysThrowsExceptionHandler<E extends Verifiable> extends AlwaysSuccessConsumeObjectHandler<E> {
        @Override
        public void onHandleObject(@Nonnull E obj) throws Exception {
            throw new RuntimeException();
        }

        @Nonnull
        @Override
        public HandleFailedStrategy onHandleObjectFailed(@Nonnull E obj, @Nonnull Throwable throwable) {
            throw new RuntimeException();
        }
    }

    private static final class ShutdownAfterFailedHandler<E extends Verifiable> extends AlwaysThrowsExceptionHandler<E> {
        @Nonnull
        @Override
        public HandleFailedStrategy onHandleObjectFailed(@Nonnull E obj, @Nonnull Throwable throwable) {
            return HandleFailedStrategy.SHUTDOWN;
        }
    }

    private static final class RetryAfterFailedHandler<E extends Verifiable> extends AlwaysSuccessConsumeObjectHandler<E> {
        private int handleTimes = 0;
        private final int expectHandleTimes;

        RetryAfterFailedHandler(int expectHandleTimes) {
            this.expectHandleTimes = expectHandleTimes;
        }

        public int getHandleTimes() {
            return handleTimes;
        }

        @Override
        public void onHandleObject(@Nonnull E obj) throws Exception {
            if (++handleTimes < expectHandleTimes) {
                throw new RuntimeException();
            }
        }

        @Nonnull
        @Override
        public HandleFailedStrategy onHandleObjectFailed(@Nonnull E obj, @Nonnull Throwable throwable) {
            return HandleFailedStrategy.RETRY;
        }
    }

    private static final class IgnoreAfterFailedHandler<E extends Verifiable> extends AlwaysThrowsExceptionHandler<E> {
        @Nonnull
        @Override
        public HandleFailedStrategy onHandleObjectFailed(@Nonnull E obj, @Nonnull Throwable throwable) {
            return HandleFailedStrategy.IGNORE;
        }
    }

    private static final class TestingConsumeObjectListener<E extends Verifiable> implements ConsumeObjectListener<E> {
        private final List<E> invalidObjects;
        private final List<E> successObjects;
        private final List<E> failedObjects;
        private final List<Throwable> failedExceptions;

        TestingConsumeObjectListener() {
            this.invalidObjects = new ArrayList<>();
            this.successObjects = new ArrayList<>();
            this.failedObjects = new ArrayList<>();
            this.failedExceptions = new ArrayList<>();
        }

        public List<E> getInvalidObjects() {
            return invalidObjects;
        }

        public List<E> getSuccessObjects() {
            return successObjects;
        }

        public List<E> getFailedObjects() {
            return failedObjects;
        }

        public List<Throwable> getFailedExceptions() {
            return failedExceptions;
        }

        @Override
        public void onInvalidObject(@Nonnull E obj) {
            invalidObjects.add(obj);
        }

        @Override
        public void onHandleSuccess(@Nonnull E obj) {
            successObjects.add(obj);
        }

        @Override
        public void onHandleFailed(@Nonnull E obj) {
            failedObjects.add(obj);
        }

        @Override
        public void onListenerNotificationFailed(@Nonnull Throwable throwable) {
            failedExceptions.add(throwable);
        }
    }
}
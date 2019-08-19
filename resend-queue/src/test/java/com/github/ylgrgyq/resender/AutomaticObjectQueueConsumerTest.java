package com.github.ylgrgyq.resender;

import org.assertj.core.api.Condition;
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
    private final Condition<Throwable> runtimeException = new Condition<>(e -> e instanceof RuntimeException, "RuntimeException");
    private final TestingStorage storage = new TestingStorage();
    private final TestingConsumeObjectListener<TestingPayload> listener = new TestingConsumeObjectListener<>();
    private final AutomaticObjectQueueConsumerBuilder<TestingPayload> builder = AutomaticObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
            .setStorage(storage)
            .setDeserializer(new TestingPayloadCodec())
            .addConsumeElementListener(listener)
            .setAutoCommit(false)
            .setListenerExecutor(Executors.newSingleThreadExecutor());

    @Before
    public void setUp() {
        storage.clear();
        listener.clear();
    }

    @Test
    public void simpleConsume() throws Exception {
        final List<ObjectWithId> storedPayload = generateTestingPayload(64, true);
        final AlwaysSuccessConsumeObjectHandler<TestingPayload> handler = new AlwaysSuccessConsumeObjectHandler<>();
        final AutomaticObjectQueueConsumer<TestingPayload> consumer = builder.setConsumeObjectHandler(handler).build();

        await().until(() -> handler.getReceivedObjects().size() == storedPayload.size());
        assertThat(handler.getReceivedObjects().stream()
                .map(TestingPayload::createPayloweWithId)
                .collect(Collectors.toList()))
                .isEqualTo(storedPayload);
        assertThat(listener.getSuccessObjects())
                .isEqualTo(handler.getReceivedObjects());
        consumer.close();
    }

    @Test
    public void consumeInvalidObject() throws Exception {
        final List<ObjectWithId> storedPayload = generateTestingPayload(64, false);

        final AlwaysSuccessConsumeObjectHandler<TestingPayload> handler = new AlwaysSuccessConsumeObjectHandler<>();

        final AutomaticObjectQueueConsumer<TestingPayload> consumer = builder
                .setConsumeObjectHandler(handler)
                .addConsumeElementListener(listener)
                .build();

        await().until(() -> listener.getInvalidObjects().size() == storedPayload.size());
        assertThat(listener.getInvalidObjects().stream()
                .map(TestingPayload::createPayloweWithId)
                .collect(Collectors.toList()))
                .isEqualTo(storedPayload);
        consumer.close();
    }

    @Test
    public void shutdownAfterConsumeObjectFailed() {
        final List<ObjectWithId> storedPayload = generateTestingPayload(1, true);

        final AutomaticObjectQueueConsumer<TestingPayload> consumer = builder
                .setConsumeObjectHandler(new AlwaysThrowsExceptionHandler<TestingPayload>() {
                    @Nonnull
                    @Override
                    public HandleFailedStrategy onHandleObjectFailed(@Nonnull TestingPayload obj, @Nonnull Throwable throwable) {
                        return HandleFailedStrategy.SHUTDOWN;
                    }
                })
                .build();

        assertThat(listener.getFailedObjects().stream()
                .map(TestingPayload::createPayloweWithId)
                .collect(Collectors.toList()))
                .isEqualTo(storedPayload);
        assertThat(listener.getFailedExceptions()).hasSize(1).are(runtimeException);
        await().until(consumer::closed);
    }

    @Test
    public void retryAfterConsumeObjectFailed() throws Exception {
        final List<ObjectWithId> storedObjects = generateTestingPayload(1, true);

        RetryAfterFailedHandler<TestingPayload> handler = new RetryAfterFailedHandler<>(10);
        final AutomaticObjectQueueConsumer<TestingPayload> consumer = builder
                .setConsumeObjectHandler(handler)
                .build();

        await().until(() -> handler.getHandleTimes() == 10);
        assertThat(listener.getFailedObjects()).hasSize(9).allMatch(o ->
                o.createPayloweWithId().equals(storedObjects.get(0)));
        assertThat(listener.getFailedExceptions()).hasSize(9).are(runtimeException);
        consumer.close();
    }

    @Test
    public void ignoreAfterConsumeObjectFailed() throws Exception {
        final List<ObjectWithId> storedObjects = generateTestingPayload(64, true);

        final List<TestingPayload> handledPayloads = new ArrayList<>();
        final AutomaticObjectQueueConsumer<TestingPayload> consumer = builder
                .setConsumeObjectHandler(new AlwaysThrowsExceptionHandler<TestingPayload>() {
                    @Nonnull
                    @Override
                    public HandleFailedStrategy onHandleObjectFailed(@Nonnull TestingPayload obj, @Nonnull Throwable throwable) {
                        handledPayloads.add(obj);
                        return HandleFailedStrategy.IGNORE;
                    }
                })
                .build();

        await().until(() -> handledPayloads.size() == storedObjects.size());
        assertThat(handledPayloads.stream()
                .map(TestingPayload::createPayloweWithId)
                .collect(Collectors.toList()))
                .isEqualTo(storedObjects);
        assertThat(listener.getFailedObjects().stream()
                .map(TestingPayload::createPayloweWithId)
                .collect(Collectors.toList()))
                .isEqualTo(storedObjects);
        assertThat(listener.getFailedExceptions()).hasSameSizeAs(storedObjects).are(runtimeException);
        consumer.close();
    }

    @Test
    public void onHandleObjectFailedThrowsException() {
        final List<ObjectWithId> storedObjects = generateTestingPayload(1, true);

        final AutomaticObjectQueueConsumer<TestingPayload> consumer = builder
                .setConsumeObjectHandler(new AlwaysThrowsExceptionHandler<TestingPayload>() {})
                .build();

        await().until(consumer::closed);
        assertThat(listener.getFailedObjects().stream()
                .map(TestingPayload::createPayloweWithId)
                .collect(Collectors.toList()))
                .isEqualTo(storedObjects);
        assertThat(listener.getFailedExceptions()).hasSize(1).are(runtimeException);
    }

    @Test
    public void onHandleObjectFailedReturnsNull() {
        final List<ObjectWithId> storedObjects = generateTestingPayload(1, true);

        final AutomaticObjectQueueConsumer<TestingPayload> consumer = builder
                .setConsumeObjectHandler(new AlwaysThrowsExceptionHandler<TestingPayload>() {
                    @Nonnull
                    @Override
                    @SuppressWarnings("ConstantConditions")
                    public HandleFailedStrategy onHandleObjectFailed(@Nonnull TestingPayload obj, @Nonnull Throwable throwable) {
                        return null;
                    }
                })
                .build();

        assertThat(listener.getFailedObjects().stream()
                .map(TestingPayload::createPayloweWithId)
                .collect(Collectors.toList()))
                .isEqualTo(storedObjects);
        assertThat(listener.getFailedExceptions()).hasSize(1).are(runtimeException);
        await().until(consumer::closed);
    }

    private List<ObjectWithId> generateTestingPayload(int size, boolean valid) {
        final List<ObjectWithId> storedPayload = new ArrayList<>();
        for (int i = 1; i < size + 1; i++) {
            TestingPayload payload = new TestingPayload(i, ("" + i).getBytes(StandardCharsets.UTF_8)).setValid(valid);
            storedPayload.add(payload.createPayloweWithId());
        }
        storage.store(storedPayload);
        return storedPayload;
    }

    private static class AlwaysSuccessConsumeObjectHandler<E extends Verifiable> implements ConsumeObjectHandler<E> {
        private final List<E> receivedPayloads;

        AlwaysSuccessConsumeObjectHandler() {
            this.receivedPayloads = new ArrayList<>();
        }

        List<E> getReceivedObjects() {
            return receivedPayloads;
        }

        @Override
        public void onHandleObject(@Nonnull E obj) {
            receivedPayloads.add(obj);
        }

        @Nonnull
        @Override
        public HandleFailedStrategy onHandleObjectFailed(@Nonnull E obj, @Nonnull Throwable throwable) {
            throw new IllegalStateException("can't be here");
        }
    }

    private static abstract class AlwaysThrowsExceptionHandler<E extends Verifiable> extends AlwaysSuccessConsumeObjectHandler<E> {
        @Override
        public void onHandleObject(@Nonnull E obj) {
            throw new RuntimeException();
        }

        @Nonnull
        @Override
        public HandleFailedStrategy onHandleObjectFailed(@Nonnull E obj, @Nonnull Throwable throwable) {
            throw new RuntimeException();
        }
    }

    private static final class RetryAfterFailedHandler<E extends Verifiable> extends AlwaysSuccessConsumeObjectHandler<E> {
        private final int expectHandleTimes;
        private int handleTimes = 0;

        RetryAfterFailedHandler(int expectHandleTimes) {
            this.expectHandleTimes = expectHandleTimes;
        }

        int getHandleTimes() {
            return handleTimes;
        }

        @Override
        public void onHandleObject(@Nonnull E obj) {
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

        void clear() {
            invalidObjects.clear();
            successObjects.clear();
            failedObjects.clear();
            failedExceptions.clear();
        }

        List<E> getInvalidObjects() {
            return invalidObjects;
        }

        List<E> getSuccessObjects() {
            return successObjects;
        }

        List<E> getFailedObjects() {
            return failedObjects;
        }

        List<Throwable> getFailedExceptions() {
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
        public void onHandleFailed(@Nonnull E obj, @Nonnull Throwable throwable) {
            failedObjects.add(obj);
            failedExceptions.add(throwable);
        }

        @Override
        public void onListenerNotificationFailed(@Nonnull Throwable throwable) {
            failedExceptions.add(throwable);
        }
    }
}
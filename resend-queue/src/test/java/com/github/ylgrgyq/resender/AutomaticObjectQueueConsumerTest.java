package com.github.ylgrgyq.resender;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class AutomaticObjectQueueConsumerTest {

    @Test
    public void simpleConsume() throws Exception {
        final TestingStorage storage = new TestingStorage();
        final ArrayList<ObjectWithId> storedPayload = new ArrayList<>();

        TestingConsumeObjectHandler<TestingPayload> handler = new TestingConsumeObjectHandler<>();
        AutomaticObjectQueueConsumer<TestingPayload> consumer = AutomaticObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(false)
                .setConsumeObjectHandler(handler)
                .setListenerExecutor(Executors.newSingleThreadExecutor())
                .build();

        for (int i = 1; i < 64; i++) {
            TestingPayload payload = new TestingPayload(i, ("" + i).getBytes(StandardCharsets.UTF_8));
            storedPayload.add(payload.createPayloweWithId());
        }
        storage.store(storedPayload);

        await().until(() -> handler.getReceivedPayloads().size() == storedPayload.size());

        consumer.close();
    }


    private static final class TestingConsumeObjectHandler<E extends Verifiable> implements ConsumeObjectHandler<E> {
        private final List<E> receivedPayloads;

        public TestingConsumeObjectHandler() {
            this.receivedPayloads = new ArrayList<>();
        }

        public List<E> getReceivedPayloads() {
            return receivedPayloads;
        }

        @Override
        public void onHandleObject(E obj) throws Exception {
            receivedPayloads.add(obj);
        }

        @Override
        public HandleFailedStrategy onHandleObjectFailed(E obj, Throwable throwable) {
            return null;
        }
    }
}
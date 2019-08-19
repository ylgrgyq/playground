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
        final ArrayList<ElementWithId> storedPayload = new ArrayList<>();

        TestingConsumeObjectHandler<TestingPayload> handler = new TestingConsumeObjectHandler<>();
        AutomaticObjectQueueConsumer<TestingPayload> consumer = AutomaticObjectQueueConsumerBuilder.newBuilder()
                .setDeserializer(new TestingPayloadCodec())
                .setAutoCommit(false)
                .setBatchSize(1024)
                .setHandler(handler)
                .setListenerExecutor(Executors.newSingleThreadExecutor())
                .build();

        consumer.start();

        for (int i = 1; i < 64; i++) {
            TestingPayload payload = new TestingPayload(i, ("" + i).getBytes(StandardCharsets.UTF_8));
            storedPayload.add(payload.createPayloweWithId());
        }
        storage.store(storedPayload);

        await().until(() -> handler.getReceivedPayloads().size() == storedPayload.size());


        System.out.println(handler.getReceivedPayloads());
        System.out.println(storedPayload);

        consumer.close();
    }

    private static final class TestingConsumeObjectHandler<E extends Payload> implements ConsumeObjectHandler<E> {
        private final List<E> receivedPayloads;

        public TestingConsumeObjectHandler() {
            this.receivedPayloads = new ArrayList<>();
        }

        public List<E> getReceivedPayloads() {
            return receivedPayloads;
        }

        @Override
        public void onReceivedObject(E payload) throws Exception {
            System.out.println("receive " + payload);
            receivedPayloads.add(payload);
        }

        @Override
        public HandleFailedObjectStrategy onReceivedObjectFailed(E failedPayload, Throwable throwable) {
            return null;
        }
    }
}
package com.github.ylgrgyq.reservoir;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RocksDbStorageTest {
    private File tempFile;

    @Before
    public void setUp() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "replicator_server_test_" + System.nanoTime();
        tempFile = new File(tempDir);
        FileUtils.forceMkdir(tempFile);
    }

    @Test
    public void commitId() throws Exception {
        RocksDbStorage storage = new RocksDbStorage(tempFile.getPath(), true);
        storage.commitId(100);
        assertThat(storage.getLastCommittedId()).isEqualTo(100);
        storage.close();
    }

    @Test
    public void simpleStore() throws Exception {
        RocksDbStorage storage = new RocksDbStorage(tempFile.getPath(), true);
        final int expectSize = 64;
        List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            ObjectWithId obj = new ObjectWithId(i, ("" + i).getBytes(StandardCharsets.UTF_8));
            objs.add(obj);
        }
        storage.store(objs);
        assertThat(storage.getLastProducedId()).isEqualTo(expectSize);
        assertThat(objs)
                .containsExactly((ObjectWithId[]) storage.fetch(0, 100).toArray(new ObjectWithId[expectSize]));
        storage.close();
    }

    @Test
    public void blockFetch() throws Exception {
        RocksDbStorage storage = new RocksDbStorage(tempFile.getPath(), true, 50);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<Collection<ObjectWithId>> f = CompletableFuture.supplyAsync(() -> {
            try {
                barrier.await();
                return storage.fetch(0, 100);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        barrier.await();
        final int expectSize = 64;
        List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            ObjectWithId obj = new ObjectWithId(i, ("" + i).getBytes(StandardCharsets.UTF_8));
            objs.add(obj);
        }
        storage.store(objs);
        assertThat(storage.getLastProducedId()).isEqualTo(expectSize);
        assertThat(objs)
                .containsExactly((ObjectWithId[]) f.get().toArray(new ObjectWithId[expectSize]));
        storage.close();
    }

    @Test
    public void blockFetchTimeout() throws Exception {
        RocksDbStorage storage = new RocksDbStorage(tempFile.getPath(), true);

        assertThat(storage.fetch(0, 100, 100, TimeUnit.MILLISECONDS)).hasSize(0);
        storage.close();
    }

    @Test
    public void truncate() throws Exception {
        RocksDbStorage storage = new RocksDbStorage(tempFile.getPath(), true, 500, 100);
        final int expectSize = 2000;
        List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            ObjectWithId obj = new ObjectWithId(i, ("" + i).getBytes(StandardCharsets.UTF_8));
            objs.add(obj);
        }
        storage.store(objs);
        storage.commitId(2000);
        await().until(() -> {
            Collection<ObjectWithId> actualObjs = storage.fetch(0, 100);
            return actualObjs.iterator().next().getId() == 1000;
        });
        storage.close();
    }

    @Test
    public void simpleProducenAndConsume() throws Exception {
        RocksDbStorage storage = new RocksDbStorage(tempFile.getPath(), true);
        ObjectQueueProducer<TestingPayload> producer = ObjectQueueProducerBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setSerializer(new TestingPayloadCodec())
                .build();
        TestingPayload payload = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        producer.produce(payload);

        ObjectQueueConsumer<TestingPayload> consumer = ObjectQueueConsumerBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setDeserializer(new TestingPayloadCodec())
                .build();

        assertThat(consumer.fetch()).isEqualTo(payload);
        storage.close();
    }
}
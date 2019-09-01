package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class FileBasedStorageTest {
    private File tempFile;

    @Before
    public void setUp() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "reservoir_test_" + System.nanoTime();
        tempFile = new File(tempDir);
        FileUtils.forceMkdir(tempFile);
    }

    @Ignore
    @Test
    public void commitId() throws Exception {
        FileBasedStorage storage = new FileBasedStorage(tempFile.getPath());
        storage.commitId(100);
        assertThat(storage.getLastCommittedId()).isEqualTo(100);
        storage.close();
    }

    @Test
    public void simpleStore() throws Exception {
        FileBasedStorage storage = new FileBasedStorage(tempFile.getPath());
        final int expectSize = 64;
        List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            ObjectWithId obj = new ObjectWithId(i, TestingUtils.numberStringBytes(i));
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
        FileBasedStorage storage = new FileBasedStorage(tempFile.getPath());
        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<List<ObjectWithId>> f = CompletableFuture.supplyAsync(() -> {
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
        FileBasedStorage storage = new FileBasedStorage(tempFile.getPath());

        assertThat(storage.fetch(0, 100, 100, TimeUnit.MILLISECONDS)).hasSize(0);
        storage.close();
    }

    @Ignore
    @Test
    public void truncate() throws Exception {
        FileBasedStorage storage = new FileBasedStorage(tempFile.getPath());
        final int expectSize = 2000;
        List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            ObjectWithId obj = new ObjectWithId(i, ("" + i).getBytes(StandardCharsets.UTF_8));
            objs.add(obj);
        }
        storage.store(objs);
        storage.commitId(2000);
        await().until(() -> {
            List<ObjectWithId> actualObjs = storage.fetch(0, 100);
            return actualObjs.iterator().next().getId() == 1000;
        });
        storage.close();
    }

    @Test
    public void simpleProducenAndConsume() throws Exception {
        FileBasedStorage storage = new FileBasedStorage(tempFile.getPath());
        ObjectQueue<TestingPayload> queue = ObjectQueueBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setCodec(new TestingPayloadCodec())
                .buildQueue();
        TestingPayload payload = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        queue.produce(payload);

        assertThat(queue.fetch()).isEqualTo(payload);
        storage.close();
    }

}
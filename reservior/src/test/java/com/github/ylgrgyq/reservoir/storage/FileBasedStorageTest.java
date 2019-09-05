package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class FileBasedStorageTest {
    private File tempFile;
    private FileBasedStorageBuilder builder;

    @Before
    public void setUp() throws Exception {
        final String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "reservoir_test_" + System.nanoTime();
        tempFile = new File(tempDir);
        FileUtils.forceMkdir(tempFile);
        builder = FileBasedStorageBuilder.newBuilder(tempFile.getPath());
    }

    @Test
    public void commitId() throws Exception {
        final FileBasedStorage storage = builder.build();
        storage.commitId(100);
        assertThat(storage.getLastCommittedId()).isEqualTo(100);
        storage.close();
    }

    @Test
    public void simpleStore() throws Exception {
        final FileBasedStorage storage = builder.build();
        final int expectSize = 64;
        final List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            final ObjectWithId obj = new ObjectWithId(i, TestingUtils.numberStringBytes(i));
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
        final FileBasedStorage storage = builder.build();
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final CompletableFuture<List<ObjectWithId>> f = CompletableFuture.supplyAsync(() -> {
            try {
                barrier.await();
                return storage.fetch(0, 100);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        barrier.await();
        final int expectSize = 64;
        final List<ObjectWithId> objs = new ArrayList<>();
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
        final FileBasedStorage storage = builder.build();

        assertThat(storage.fetch(0, 100, 100, TimeUnit.MILLISECONDS)).hasSize(0);
        storage.close();
    }

    @Test
    public void testMemtableFull() throws Exception {
        final FileBasedStorage storage = builder.build();

        final int expectSize = 2;
        final List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            ObjectWithId obj = new ObjectWithId(i, new byte[Constant.kMaxMemtableSize]);
            objs.add(obj);
        }

        storage.store(objs);
        assertThat(storage.fetch(0, 100, 100, TimeUnit.MILLISECONDS)).hasSize(2);
    }

    @Test
    public void truncate() throws Exception {
        final FileBasedStorage storage = builder
                .setTruncateIntervalMillis(0, TimeUnit.MILLISECONDS)
                .build();
        final int expectSize = 2000;
        final List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            ObjectWithId obj = new ObjectWithId(i, new byte[Constant.kLogBlockSize]);
            objs.add(obj);
        }
        storage.store(objs);
        storage.commitId(1000);
        objs.add(new ObjectWithId(1010101, "Trigger truncate".getBytes(StandardCharsets.UTF_8)));

        final List<ObjectWithId> actualObjs = storage.fetch(0, 100);
        assertThat(actualObjs.iterator().next().getId()).isGreaterThan(1);

        storage.close();
    }

    @Test
    public void simpleProducenAndConsume() throws Exception {
        final FileBasedStorage storage = builder.build();
        final ObjectQueue<TestingPayload> queue = ObjectQueueBuilder.<TestingPayload>newBuilder()
                .setStorage(storage)
                .setCodec(new TestingPayloadCodec())
                .buildQueue();
        final TestingPayload payload = new TestingPayload(1, "first".getBytes(StandardCharsets.UTF_8));
        queue.produce(payload);

        assertThat(queue.fetch()).isEqualTo(payload);
        storage.close();
    }
}
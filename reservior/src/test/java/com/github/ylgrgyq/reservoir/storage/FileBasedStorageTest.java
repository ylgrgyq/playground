package com.github.ylgrgyq.reservoir.storage;

import com.github.ylgrgyq.reservoir.*;
import com.github.ylgrgyq.reservoir.storage.FileName.FileNameMeta;
import com.github.ylgrgyq.reservoir.storage.FileName.FileType;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static com.github.ylgrgyq.reservoir.TestingUtils.numberStringBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    public void commitThenGetCommitId() throws Exception {
        final FileBasedStorage storage = builder.build();
        storage.commitId(100);
        assertThat(storage.getLastCommittedId()).isEqualTo(100);
        storage.close();
    }

    @Test
    public void consecutiveCommitThenGetCommitIdAfterRecoverUsingSameFile() throws Exception {
        FileBasedStorage storage = builder.build();
        final FileNameMeta expectMeta = FileName.getFileNameMetas(tempFile.getPath(),
                meta -> meta.getType() == FileType.ConsumerCommit).get(0);

        for (int i = 1; i < 100; i++) {
            storage.commitId(i);
            storage.close();
            storage = builder.build();
            assertThat(storage.getLastCommittedId()).isEqualTo(i);
        }
        assertThat(FileName.getFileNameMetas(tempFile.getPath(), meta -> meta.getType() == FileType.ConsumerCommit))
                .hasSize(1).allMatch(meta -> meta.getFileNumber() == expectMeta.getFileNumber());
        storage.close();
    }

    @Test
    public void blockFetch() throws Exception {
        final FileBasedStorage storage = builder.build();
        final CyclicBarrier barrier = new CyclicBarrier(2);
        // block fetch in another thread
        final CompletableFuture<List<ObjectWithId>> f = CompletableFuture.supplyAsync(() -> {
            try {
                barrier.await();
                return storage.fetch(0, 100);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // waiting fetch thread in position, then feed some data in storage
        barrier.await();
        final int expectSize = 64;
        final List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            ObjectWithId obj = new ObjectWithId(i, numberStringBytes(i));
            objs.add(obj);
        }
        storage.store(objs);
        assertThat(storage.getLastProducedId()).isEqualTo(expectSize);
        assertThat(f.get()).containsExactlyElementsOf(objs);
        storage.close();
    }

    @Test
    public void blockFetchWithTimeout() throws Exception {
        final FileBasedStorage storage = builder.build();

        assertThat(storage.fetch(0, 100, 100, TimeUnit.MILLISECONDS)).hasSize(0);
        storage.close();
    }

    @Test
    public void storeDuplicateData() throws Exception {
        final FileBasedStorage storage = builder.build();
        final int expectSize = 64;

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(expectSize);
        storage.store(objs);
        assertThatThrownBy(() -> storage.store(objs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data being appended is not monotone increasing");

        storage.close();
    }

    @Test
    public void storeDataWithIdNotMonotoneIncreasing() throws Exception {
        final FileBasedStorage storage = builder.build();

        storage.store(Collections.singletonList(new ObjectWithId(1, numberStringBytes(1))));
        storage.store(Collections.singletonList(new ObjectWithId(2, numberStringBytes(2))));

        assertThatThrownBy(() ->
                storage.store(Collections.singletonList(new ObjectWithId(2, numberStringBytes(2)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data being appended is not monotone increasing");

        storage.close();
    }

    @Test
    public void fetchDataFromMemtable() throws Exception {
        final FileBasedStorage storage = builder.build();
        final int expectSize = 64;

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(expectSize);
        storage.store(objs);

        assertThat(storage.getLastProducedId()).isEqualTo(expectSize);
        assertThat(objs)
                .containsExactly((ObjectWithId[]) storage.fetch(0, 100).toArray(new ObjectWithId[expectSize]));
        storage.close();
    }

    @Test
    public void fetchDataFromRecoveredMemtable() throws Exception {
        FileBasedStorage storage = builder.build();
        final int expectSize = 64;

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(expectSize);
        storage.store(objs);
        storage.close();

        storage = builder.build();
        assertThat(storage.getLastProducedId()).isEqualTo(expectSize);
        assertThat(objs)
                .containsExactly((ObjectWithId[]) storage.fetch(0, 100).toArray(new ObjectWithId[expectSize]));
        storage.close();
    }

    @Test
    public void fetchDataFromImmutableMemtable() throws Exception {
        final FileBasedStorage storage = builder
                // block flush memtable job, so immutable memtable will stay in mem during the test
                .setFlushMemtableExecutorService(new DelayedSingleThreadExecutorService(1, TimeUnit.DAYS))
                .build();

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(64);
        storage.store(objs);

        triggerFlushMemtable(storage, 1000);
        assertThat(storage.fetch(1, 64)).containsExactlyElementsOf(objs);
        storage.close();
    }

    @Test
    public void fetchDataFromRecoveredImmutableMemtable() throws Exception {
        FileBasedStorage storage = builder
                // block flush memtable job, so immutable memtable will stay in mem during the test
                .setFlushMemtableExecutorService(new DelayedSingleThreadExecutorService(1, TimeUnit.DAYS))
                .build();

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(64);
        storage.store(objs);
        triggerFlushMemtable(storage, 1000);
        storage.close();
        storage = builder
                // create executor service again, because the previous executor service in the storage
                // builder is closed when the previous storage closed
                .setFlushMemtableExecutorService(new DelayedSingleThreadExecutorService(1, TimeUnit.DAYS))
                .build();
        assertThat(storage.fetch(1, 64)).containsExactlyElementsOf(objs);
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

    private List<ObjectWithId> generateSimpleTestingObjectWithIds(int expectSize) {
        final List<ObjectWithId> objs = new ArrayList<>();
        for (int i = 1; i < expectSize + 1; i++) {
            final ObjectWithId obj = new ObjectWithId(i, numberStringBytes(i));
            objs.add(obj);
        }
        return objs;
    }

    private List<ObjectWithId> triggerFlushMemtable(FileBasedStorage storage, long nextId) throws StorageException {
        List<ObjectWithId> triggerDatas = Arrays.asList(new ObjectWithId(nextId, new byte[Constant.kMaxMemtableSize]),
                new ObjectWithId(nextId + 1, numberStringBytes(nextId + 1)));
        storage.store(triggerDatas);
        return triggerDatas;
    }
}
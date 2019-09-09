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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.github.ylgrgyq.reservoir.TestingUtils.numberStringBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
                return storage.fetch(Integer.MIN_VALUE, 100);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // waiting fetch thread in position, then feed some data in storage
        barrier.await();
        final int expectSize = 64;
        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(expectSize);
        storeObjectWithIds(storage, objs);
        assertThat(storage.getLastProducedId()).isEqualTo(expectSize - 1);
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
    public void fetchDataFromMemtable() throws Exception {
        final FileBasedStorage storage = builder.build();
        final int expectSize = 64;

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(expectSize);
        storeObjectWithIds(storage, objs);
        assertThat(storage.getLastProducedId()).isEqualTo(expectSize - 1);

        for (int i = -1; i < expectSize; i += 10) {
            assertThat(storage.fetch(i, 100)).isEqualTo(objs.subList(i + 1, objs.size()));
        }
        storage.close();
    }

    @Test
    public void fetchDataFromRecoveredMemtable() throws Exception {
        FileBasedStorage storage = builder.build();
        final int expectSize = 64;

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(expectSize);
        storeObjectWithIds(storage, objs);
        storage.close();

        storage = builder.build();
        assertThat(storage.getLastProducedId()).isEqualTo(expectSize - 1);
        assertThat(storage.fetch(-1, 100)).isEqualTo(objs);
        storage.close();
    }

    @Test
    public void fetchDataFromImmutableMemtable() throws Exception {
        final FileBasedStorage storage = builder
                // block flush memtable job, so immutable memtable will stay in mem during the test
                .setFlushMemtableExecutorService(new DelayedSingleThreadExecutorService(1, TimeUnit.DAYS))
                .build();

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(64);
        storeObjectWithIds(storage, objs);

        triggerFlushMemtable(storage);
        assertThat(storage.fetch(-1, 64)).isEqualTo(objs);
        storage.close(true);
    }

    @Test
    public void fetchDataOnlyFromImmutableMemtableAndMemtable() throws Exception {
        final FileBasedStorage storage = builder
                // block flush memtable job, so immutable memtable will stay in mem during the test
                .setFlushMemtableExecutorService(new DelayedSingleThreadExecutorService(1, TimeUnit.DAYS))
                .build();

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(64);
        storeObjectWithIds(storage, objs);

        objs.addAll(triggerFlushMemtable(storage));
        assertThat(storage.fetch(31, 1000)).isEqualTo(objs.subList(32, objs.size()));
        storage.close(true);
    }


    @Test
    public void fetchDataFromRecoveredSstable() throws Exception {
        FileBasedStorage storage = builder
                // block flush memtable job, so immutable memtable will stay in mem during the test
                .setFlushMemtableExecutorService(new DelayedSingleThreadExecutorService(1, TimeUnit.DAYS))
                .build();

        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(64);
        storeObjectWithIds(storage, objs);
        triggerFlushMemtable(storage);
        storage.close(true);
        storage = builder
                // create executor service again, because the previous executor service in the storage
                // builder is closed when the previous storage closed
                .setFlushMemtableExecutorService(new DelayedSingleThreadExecutorService(1, TimeUnit.DAYS))
                .build();
        assertThat(storage.fetch(-1, 64)).isEqualTo(objs);
        storage.close(true);
    }

    @Test
    public void fetchDataFromSstableImmutableMemtableAndMemtable() throws Exception {
        final FileBasedStorage storage = builder
                // allow only one flush task finish immediately
                .setFlushMemtableExecutorService(new DelayedSingleThreadExecutorService(1, 1, TimeUnit.DAYS))
                .build();

        // 1. write some data
        final List<ObjectWithId> expectData = generateSimpleTestingObjectWithIds(64);
        storeObjectWithIds(storage, expectData);
        // 2. flush for the first time, make every data write before flushed to sstable
        expectData.addAll(triggerFlushMemtable(storage));
        // 3. write some more data
        final List<ObjectWithId> objInMem = generateSimpleTestingObjectWithIds(storage.getLastProducedId() + 1, 128);
        expectData.addAll(objInMem);
        storeObjectWithIds(storage, objInMem);
        // 4. flush again, but this time the flush task will be blocked so
        // every data write in step 3 will be stay in immutable table and the data triggering
        // the flush task will stay in memtable
        expectData.addAll(triggerFlushMemtable(storage));

        // add one to limit to ensure there's no more data in storage than in expectData
        assertThat(storage.fetch(-1, expectData.size() + 1)).isEqualTo(expectData);
        storage.close(true);
    }

    @Test
    public void fetchDataFromMultiSstables() throws Exception {
        final FileBasedStorage storage = builder
                .setFlushMemtableExecutorService(new ImmediateExecutorService())
                .build();

        final List<ObjectWithId> expectData = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            List<ObjectWithId> batch = generateSimpleTestingObjectWithIds(storage.getLastProducedId() + 1, 64);
            expectData.addAll(batch);
            storeObjectWithIds(storage, batch);
            expectData.addAll(triggerFlushMemtable(storage));
        }

        assertThat(storage.fetch(-1, expectData.size() + 1)).isEqualTo(expectData);
        storage.close();
    }


    @Test
    // add this to pass null to writeMemtable to throw exception
    @SuppressWarnings("ConstantConditions")
    public void testFlushMemtableFailedThenStorageClosed() throws Exception {
        final FileBasedStorage storage = builder
                .setFlushMemtableExecutorService(new ImmediateExecutorService())
                .build();

        storage.writeMemtable(null);
        await().until(storage::closed);
    }

    @Test
    public void truncate() throws Exception {
        final FileBasedStorage storage = builder
                .setFlushMemtableExecutorService(new ImmediateExecutorService())
                .setTruncateIntervalMillis(0, TimeUnit.MILLISECONDS)
                .build();
        final int expectSize = 65;
        final List<ObjectWithId> objs = generateSimpleTestingObjectWithIds(expectSize);
        storeObjectWithIds(storage, objs);
        objs.addAll(triggerFlushMemtable(storage));
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
        return generateSimpleTestingObjectWithIds(0L, expectSize);
    }

    private List<ObjectWithId> generateSimpleTestingObjectWithIds(long startId, int expectSize) {
        final List<ObjectWithId> objs = new ArrayList<>();
        for (long i = startId; i < startId + expectSize; i++) {
            final ObjectWithId obj = new ObjectWithId(i, numberStringBytes(i));
            objs.add(obj);
        }
        return objs;
    }

    private long lastIdInBatch(List<ObjectWithId> batch) {
        assert !batch.isEmpty();
        return batch.get(batch.size() - 1).getId();
    }

    private void storeObjectWithIds(FileBasedStorage storage, List<ObjectWithId> batch) throws StorageException {
        storage.store(batch.stream().map(ObjectWithId::getObjectInBytes).collect(Collectors.toList()));
    }

    private List<ObjectWithId> triggerFlushMemtable(FileBasedStorage storage) throws StorageException {
        long nextId = storage.getLastProducedId() + 1;
        List<ObjectWithId> triggerDatas = Arrays.asList(
                new ObjectWithId(nextId, new byte[Constant.kMaxMemtableSize]),
                new ObjectWithId(nextId + 1, numberStringBytes(nextId + 1)));
        storeObjectWithIds(storage, triggerDatas);
        return triggerDatas;
    }
}
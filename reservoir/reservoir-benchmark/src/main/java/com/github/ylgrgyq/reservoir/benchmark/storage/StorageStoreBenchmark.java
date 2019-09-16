package com.github.ylgrgyq.reservoir.benchmark.storage;

import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.github.ylgrgyq.reservoir.FileUtils;
import com.github.ylgrgyq.reservoir.ObjectQueueStorage;
import com.github.ylgrgyq.reservoir.StorageException;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

abstract class StorageStoreBenchmark implements BenchmarkTest {
    private final int numBatches;
    private final int dataSize;
    private final int numDataPerBatch;
    private final List<List<byte[]>> testingData;
    private final String baseDir;
    @Nullable
    private ObjectQueueStorage<byte[]> storage;
    @Nullable
    private Timer timer;

    StorageStoreBenchmark(int dataSize, int numDataPerBatch, int numBatches) {
        this.dataSize = dataSize;
        this.numBatches = numBatches;
        this.numDataPerBatch = numDataPerBatch;
        this.testingData = new ArrayList<>(TestingDataGenerator.generate(dataSize, numBatches, numDataPerBatch));

        final String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "reservoir_benchmark_" + System.nanoTime();
        final File tempFile = new File(tempDir);
        this.baseDir = tempFile.getPath();
    }

    @Override
    public void setup() throws Exception {
        // Use new storage and timer every time to prevent interference between each test
        final String tempDir = baseDir + File.separator + "test_only_store" + System.nanoTime();
        final File tempFile = new File(tempDir);
        FileUtils.forceMkdir(tempFile);
        storage = createStorage(tempFile.getPath());
        timer = new Timer();
    }

    @Override
    public void teardown() throws Exception {
        assert storage != null;
        storage.close();
    }

    @Override
    public String testingSpec() {
        return "description: " + getTestDescription() + "\n" +
                "storage path: " + baseDir + "\n" +
                "size in bytes for each data: " + dataSize + "\n" +
                "number of data per batch: " + numDataPerBatch + "\n" +
                "number of batches: " + numBatches;
    }

    @Override
    public BenchmarkTestReport runTest() throws StorageException {
        assert timer != null;
        assert storage != null;
        final long start = System.nanoTime();
        for (List<byte[]> data : testingData) {
            final Context cxt = timer.time();
            try {
                storage.store(data);
            } finally {
                cxt.stop();
            }
        }

        return new DefaultBenchmarkReport(System.nanoTime() - start, timer);
    }

    abstract String getTestDescription();

    abstract ObjectQueueStorage<byte[]> createStorage(String baseDir) throws Exception;
}

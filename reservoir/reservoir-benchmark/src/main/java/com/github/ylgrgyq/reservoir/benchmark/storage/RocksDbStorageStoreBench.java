package com.github.ylgrgyq.reservoir.benchmark.storage;

import com.github.ylgrgyq.reservoir.ObjectQueueStorage;

public final class RocksDbStorageStoreBench extends StorageStoreBenchmark {
    RocksDbStorageStoreBench(int dataSize, int numDataPerBatch, int numBatches) {
        super(dataSize, numDataPerBatch, numBatches);
    }

    @Override
    ObjectQueueStorage<byte[]> createStorage(String baseDir) throws Exception {
        return new RocksDbStorage(baseDir);
    }

    @Override
    public String getTestDescription() {
        return "Store data to RocksDbStorage test";
    }
}

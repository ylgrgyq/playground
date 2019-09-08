package com.github.ylgrgyq.reservoir.benchmark.storage;

import com.github.ylgrgyq.reservoir.ObjectQueueStorage;
import com.github.ylgrgyq.reservoir.storage.FileBasedStorageBuilder;

public class FileStorageStoreBench extends StorageStoreBenchmark {
    FileStorageStoreBench(int dataSize, int numDataPerBatch, int numBatches) {
        super(dataSize, numDataPerBatch, numBatches);
    }

    @Override
    public String getTestDescription() {
        return "Store data to FileStorage test";
    }

    @Override
    ObjectQueueStorage createStorage(String baseDir) throws Exception {
        return FileBasedStorageBuilder
                .newBuilder(baseDir)
                .build();
    }
}

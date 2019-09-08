package com.github.ylgrgyq.reservoir.benchmark.storage;

import com.github.ylgrgyq.reservoir.ObjectWithId;

import java.util.ArrayList;
import java.util.List;

class TestingDataGenerator {
    static List<List<ObjectWithId>> generate(int dataSize, int dataCount, int batchSize) {
        final List<List<ObjectWithId>> testingData = new ArrayList<>();

        long id = 1;
        for (int i = 0; i < dataCount; i++) {
            List<ObjectWithId> batch = generateBatch(id, batchSize, dataSize);
            testingData.add(batch);
            id += batchSize;
        }

        return testingData;
    }

    static List<ObjectWithId> generateBatch(long startId, int batchSize, int dataSize) {
        long id = startId;
        final List<ObjectWithId> batch = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            final ObjectWithId data = new ObjectWithId(id++, TestingUtils.makeStringInBytes("Hello", dataSize));
            batch.add(data);
        }
        return batch;
    }
}

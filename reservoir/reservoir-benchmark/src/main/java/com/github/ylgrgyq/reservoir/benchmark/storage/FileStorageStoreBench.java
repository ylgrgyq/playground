package com.github.ylgrgyq.reservoir.benchmark.storage;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.github.ylgrgyq.reservoir.FileUtils;
import com.github.ylgrgyq.reservoir.ObjectWithId;
import com.github.ylgrgyq.reservoir.StorageException;
import com.github.ylgrgyq.reservoir.storage.FileBasedStorage;
import com.github.ylgrgyq.reservoir.storage.FileBasedStorageBuilder;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class FileStorageStoreBench implements BenchmarkTest {
    private static final String timerMetricsName = "file-storage-store-timer";
    private MetricRegistry metrics;
    private int dataToStoreCount;
    private int dataToStoreSize;
    private int batchSize;
    private List<List<ObjectWithId>> testingData;
    @Nullable
    private FileBasedStorage storage;
    @Nullable
    private Timer timer;

    FileStorageStoreBench(int dataToStoreCount, int dataToStoreSize, int batchSize) {
        this.metrics = new MetricRegistry();
        this.dataToStoreSize = dataToStoreSize;
        this.dataToStoreCount = dataToStoreCount;
        this.batchSize = batchSize;
        this.testingData = new ArrayList<>(TestingDataGenerator.generate(dataToStoreSize, dataToStoreCount, batchSize));
    }

    @Override
    public void setup() throws Exception {
        // Use new storage and timer every time to prevent interference between each test
        final String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "reservoir_test_bench_" + System.nanoTime();
        final File tempFile = new File(tempDir);
        FileUtils.forceMkdir(tempFile);
        storage = FileBasedStorageBuilder
                .newBuilder(tempFile.getPath())
                .build();
        timer = metrics.timer(timerMetricsName);
    }

    @Override
    public void teardown() {
        metrics.remove(timerMetricsName);
    }

    @Override
    public String testingSpec() {
        return "name: Test store data to FileStorage\n" +
                "dataSize: " + dataToStoreSize + "\n" +
                "dataCount: " + dataToStoreCount + "\n" +
                "batchSize: " + batchSize;
    }

    @Override
    public BenchmarkTestReport runTest() throws StorageException {
        assert timer != null;
        assert storage != null;
        final long start = System.nanoTime();
        for (List<ObjectWithId> data : testingData) {
            final Context cxt = timer.time();
            try {
                storage.store(data);
            } finally {
                cxt.stop();
            }
        }

        return new FileStorageStoreReport(System.nanoTime() - start, timer);
    }

    private static class FileStorageStoreReport implements BenchmarkTestReport {
        private final long testTimeElapsed;
        private final Timer timer;

        FileStorageStoreReport(long testTimeElapsed, Timer timer) {
            this.testTimeElapsed = testTimeElapsed;
            this.timer = timer;
        }

        @Override
        public String report() {
            final Snapshot snapshot = timer.getSnapshot();
            return String.format("      time elapsed = %2.2f %s\n", convertDuration(testTimeElapsed), getDurationUnit()) +
                    String.format("         mean rate = %2.2f calls/%s\n", timer.getMeanRate(), getRateUnit()) +
                    String.format("     1-minute rate = %2.2f calls/%s\n", timer.getOneMinuteRate(), getRateUnit()) +
                    String.format("               min = %2.2f %s\n", convertDuration(snapshot.getMin()), getDurationUnit()) +
                    String.format("               max = %2.2f %s\n", convertDuration(snapshot.getMax()), getDurationUnit()) +
                    String.format("              mean = %2.2f %s\n", convertDuration(snapshot.getMean()), getDurationUnit()) +
                    String.format("            stddev = %2.2f %s\n", convertDuration(snapshot.getStdDev()), getDurationUnit()) +
                    String.format("            median = %2.2f %s\n", convertDuration(snapshot.getMedian()), getDurationUnit()) +
                    String.format("              75%% <= %2.2f %s\n", convertDuration(snapshot.get75thPercentile()), getDurationUnit()) +
                    String.format("              95%% <= %2.2f %s\n", convertDuration(snapshot.get95thPercentile()), getDurationUnit()) +
                    String.format("              98%% <= %2.2f %s\n", convertDuration(snapshot.get98thPercentile()), getDurationUnit()) +
                    String.format("              99%% <= %2.2f %s\n", convertDuration(snapshot.get99thPercentile()), getDurationUnit()) +
                    String.format("            99.9%% <= %2.2f %s\n", convertDuration(snapshot.get999thPercentile()), getDurationUnit());
        }

        @Override
        public String toString() {
            return report();
        }

        private String getDurationUnit() {
            return TimeUnit.MICROSECONDS.toString().toLowerCase(Locale.US);
        }

        private String getRateUnit() {
            final String s = TimeUnit.SECONDS.toString().toLowerCase(Locale.US);
            return s.substring(0, s.length() - 1);
        }

        private double convertDuration(double duration) {
            return duration / TimeUnit.MICROSECONDS.toNanos(1);
        }
    }
}

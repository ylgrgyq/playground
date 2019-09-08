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

import java.io.File;
import java.io.IOException;
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
    private FileBasedStorage storage;
    private Timer timer;


    public FileStorageStoreBench(int dataToStoreCount, int dataToStoreSize, int batchSize) {
        this.metrics = new MetricRegistry();
        this.dataToStoreSize = dataToStoreSize;
        this.dataToStoreCount = dataToStoreCount;
        this.batchSize = batchSize;
        this.testingData = new ArrayList<>(TestingDataGenerator.generate(dataToStoreSize, dataToStoreCount, batchSize));
    }

    @Override
    public void setup() throws Exception {
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
    public String testInfo() {
        StringBuilder builder = new StringBuilder();
        builder.append("Test store data to FileStorage with parameters:");
        builder.append("dataSize:");
        builder.append(dataToStoreSize);
        builder.append(" dataCount:");
        builder.append(dataToStoreCount);
        builder.append("batchSize:");
        builder.append(batchSize);
        return builder.toString();
    }

    @Override
    public BenchmarkTestReport runTest() throws StorageException {
        for (List<ObjectWithId> data : testingData) {
            final Context cxt = timer.time();
            try {
                storage.store(data);
            } finally {
                cxt.stop();
            }
        }

        return new FileStorageStoreReport();
    }

    private class FileStorageStoreReport implements BenchmarkTestReport {
        private Locale locale;

        public FileStorageStoreReport() {
            this.locale = Locale.getDefault();
        }

        @Override
        public String report() {
            final Snapshot snapshot = timer.getSnapshot();
            final StringBuilder builder = new StringBuilder();
            builder.append(String.format(locale, "             count = %d", timer.getCount()));
            builder.append(String.format(locale, "         mean rate = %2.2f calls/%s", convertRate(timer.getMeanRate()), getRateUnit()));

            builder.append(String.format(locale, "     1-minute rate = %2.2f calls/%s", convertRate(timer.getOneMinuteRate()), getRateUnit()));
            builder.append(String.format(locale, "     5-minute rate = %2.2f calls/%s", convertRate(timer.getFiveMinuteRate()), getRateUnit()));
            builder.append(String.format(locale, "    15-minute rate = %2.2f calls/%s", convertRate(timer.getFifteenMinuteRate()), getRateUnit()));

//            builder.append(String.format(locale, "               min = %2.2f %s", convertDuration(snapshot.getMin()), getDurationUnit()));
//            builder.append(String.format(locale, "               max = %2.2f %s", convertDuration(snapshot.getMax()), getDurationUnit()));
//            builder.append(String.format(locale, "              mean = %2.2f %s", convertDuration(snapshot.getMean()), getDurationUnit()));
//            builder.append(String.format(locale, "            stddev = %2.2f %s", convertDuration(snapshot.getStdDev()), getDurationUnit()));
//            builder.append(String.format(locale, "            median = %2.2f %s", convertDuration(snapshot.getMedian()), getDurationUnit()));
//            builder.append( String.format(locale, "              75%% <= %2.2f %s", convertDuration(snapshot.get75thPercentile()), getDurationUnit()));
//            builder.append(String.format(locale, "              95%% <= %2.2f %s", convertDuration(snapshot.get95thPercentile()), getDurationUnit()));
//            builder.append(String.format(locale, "              98%% <= %2.2f %s", convertDuration(snapshot.get98thPercentile()), getDurationUnit()));
//            builder.append(String.format(locale, "              99%% <= %2.2f %s", convertDuration(snapshot.get99thPercentile()), getDurationUnit()));
//            builder.append(String.format(locale, "            99.9%% <= %2.2f %s", convertDuration(snapshot.get999thPercentile()), getDurationUnit()));

            return builder.toString();
        }

        @Override
        public String toString() {
            return report();
        }

        private String getRateUnit() {
            return calculateRateUnit(TimeUnit.SECONDS);
        }

//        private String getDurationUnit() {
//            durationUnit.toString().toLowerCase(Locale.US);
//            return durationUnit;
//        }

        protected double convertRate(double rate) {
            return rate;
        }

        private String calculateRateUnit(TimeUnit unit) {
            final String s = unit.toString().toLowerCase(Locale.US);
            return s.substring(0, s.length() - 1);
        }
    }
}

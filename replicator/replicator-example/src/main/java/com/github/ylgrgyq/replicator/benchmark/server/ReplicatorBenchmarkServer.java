package com.github.ylgrgyq.replicator.benchmark.server;

import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import com.github.ylgrgyq.replicator.server.ReplicatorServer;
import com.github.ylgrgyq.replicator.server.ReplicatorServerOptions;
import com.github.ylgrgyq.replicator.server.sequence.SequenceAppender;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;
import com.github.ylgrgyq.replicator.server.storage.StorageOptions;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ReplicatorBenchmarkServer {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorBenchmarkServer.class);

    public static void main(String[] args) throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "replicator_benchmark_server_test_" + System.nanoTime();
        File tempFile = new File(tempDir);
        FileUtils.forceMkdir(tempFile);

        StorageOptions storageOptions = StorageOptions.builder()
                .setStoragePath(tempFile.getPath())
                .setDestroyPreviousDbFiles(true)
                .build();

        ReplicatorServerOptions options = ReplicatorServerOptions.builder()
                .setPort(8888)
                .setStorageOptions(storageOptions)
                .build();

        ReplicatorServer server = new ReplicatorServer(options);

        CompletableFuture<Void> finish = new CompletableFuture<>();
        server.start().whenComplete((unused, cause) -> {
            if (cause != null) {
                logger.warn("Start server failed", cause);
                return;
            }

            new Thread(() -> {
                try {
                    logger.info("Start append log test...");
                    long nextId = 1;
                    long logsCount = 1000000;
                    for (int i = 1; i <= 5; ++i) {
                        logger.info("Start append log for the {} round with nextId: {}", i, nextId);

                        long start = System.nanoTime();
                        nextId = runTest(server, nextId, logsCount);
                        long duration = System.nanoTime() - start;
                        logger.info("Test finished for the {} round.", i);
                        logger.info("Append {} logs in {} milliseconds.", logsCount, TimeUnit.NANOSECONDS.toMillis(duration));

                        Thread.sleep(5000);

                        logger.info("The {} round append log test succeed", i);
                    }
                    finish.complete(null);
                } catch (Exception ex) {
                    logger.error("Replicator server append logs failed", ex);
                }
            }).start();
        });

        finish.get();
    }

    private static long runTest(ReplicatorServer server, long nextId, long logsCount) {
        try {
            long id = nextId;
            SequenceOptions sequenceOptions = SequenceOptions.builder()
                    .build();
            SequenceAppender appender = server.createSequence("benchmark", sequenceOptions);
            for (; id < nextId + logsCount; ++id) {
                String msg = "wahaha-" + id;
                appender.append(id, msg.getBytes(StandardCharsets.UTF_8));
            }
            return id;
        } catch (Exception ex) {
            throw new ReplicatorException("Replicator server append logs failed", ex);
        }
    }
}

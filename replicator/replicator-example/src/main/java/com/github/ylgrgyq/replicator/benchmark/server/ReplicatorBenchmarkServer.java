package com.github.ylgrgyq.replicator.benchmark.server;

import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import com.github.ylgrgyq.replicator.server.ReplicatorServer;
import com.github.ylgrgyq.replicator.server.ReplicatorServerImpl;
import com.github.ylgrgyq.replicator.server.ReplicatorServerOptions;
import com.github.ylgrgyq.replicator.server.connection.tcp.NettyReplicateChannel;
import com.github.ylgrgyq.replicator.server.sequence.SequenceAppender;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;
import com.github.ylgrgyq.replicator.server.storage.StorageOptions;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ReplicatorBenchmarkServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyReplicateChannel.class);

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

        ReplicatorServerImpl server = new ReplicatorServerImpl(options);

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

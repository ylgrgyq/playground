package com.github.ylgrgyq.replicator.example.server;

import com.github.ylgrgyq.replicator.common.entity.Snapshot;
import com.github.ylgrgyq.replicator.server.ReplicatorServerImpl;
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
import java.util.concurrent.atomic.AtomicLong;

public class ReplicatorServer {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorServer.class);

    public static void main(String[] args) throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "replicator_server_test_" + System.nanoTime();
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

        new Thread(() -> {
            try {
                AtomicLong nexId = new AtomicLong(1);
                SequenceOptions sequenceOptions = SequenceOptions.builder()
                        .setSnapshotGenerator(() -> {
                            long id = nexId.get();
                            Snapshot snapshot = new Snapshot();
                            snapshot.setId(id);
                            snapshot.setData(String.format("snapshot before %s", id).getBytes(StandardCharsets.UTF_8));
                            return snapshot;
                        })
                        .setGenerateSnapshotInterval(30, TimeUnit.SECONDS)
                        .build();
                SequenceAppender appender = server.createSequence("hahaha", sequenceOptions);
                for (; nexId.get() < 1000L; nexId.incrementAndGet()) {
                    String msg = "wahaha-" + nexId;
                    logger.info("append {} {}", nexId, msg);
                    appender.append(nexId.get(), msg.getBytes(StandardCharsets.UTF_8));
                    try {
                        Thread.sleep(100);
                    } catch (Exception ex) {
                        logger.error("exception", ex);
                    }
                }

                logger.info("generate log done {}");
            } catch (Exception ex) {
                logger.error("Replicator server append logs failed", ex);
            }
        }).start();

        final CompletableFuture<Void> shutdown = new CompletableFuture<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.shutdown();
                shutdown.complete(null);
            } catch (InterruptedException ex) {
                logger.error("Server graceful shutdown was interrupted");
            } catch (Throwable t) {
                logger.error("shutdown got exception", t);
            }
        }));

        shutdown.get();
    }
}

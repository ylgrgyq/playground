package com.github.ylgrgyq.replicator.example.server;

import com.github.ylgrgyq.replicator.server.*;
import com.github.ylgrgyq.replicator.server.connection.websocket.NettyReplicateChannel;
import com.github.ylgrgyq.replicator.server.ReplicatorServerImpl;
import com.github.ylgrgyq.replicator.server.sequence.SequenceAppender;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;
import com.github.ylgrgyq.replicator.server.storage.StorageOptions;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ReplicatorServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyReplicateChannel.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "replicator_server_test_" + System.nanoTime();
        File tempFile = new File(tempDir);
        FileUtils.forceMkdir(tempFile);

        StorageOptions storageOptions = StorageOptions.builder()
                .setStoragePath(tempFile.getPath())
                .build();

        ReplicatorServerOptions options = ReplicatorServerOptions.builder()
                .setPort(8888)
                .setStorageOptions(storageOptions)
                .build();

        ReplicatorServerImpl server = new ReplicatorServerImpl(options);

        new Thread(() -> {
            try {
                SequenceAppender appender = server.createSequence("hahaha", new SequenceOptions());
                for (int i = 1; i < 10000; ++i) {
                    String msg = "wahaha-" + i;
                    logger.info("append {} {}", i, msg);
                    appender.append(i, msg.getBytes(StandardCharsets.UTF_8));
                    try {
//                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        logger.error("exception", ex);
                    }
                }

                logger.info("generate log done {}");
            } catch (Exception ex) {
                logger.error("Replicator server append logs failed", ex);
            }
        }).start();

    }
}

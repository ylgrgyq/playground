package com.github.ylgrgyq.replicator.example.client;

import com.github.ylgrgyq.replicator.client.ReplicatorClientOptions;
import com.github.ylgrgyq.replicator.client.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class ReplicatorClient {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClient.class);

    public static void main(String[] args) throws Exception {
        String storagePath = System.getProperty("java.io.tmpdir", "/tmp") +
                File.separator + "replicator_client_test";

        ReplicatorClientOptions options = ReplicatorClientOptions
                .builder()
                .setSnapshotStoragePath(storagePath)
                .setMaxSnapshotsToKeep(0)
                .setUri(new URI("ws://localhost:8888"))
                .build();
        com.github.ylgrgyq.replicator.client.ReplicatorClient client = new com.github.ylgrgyq.replicator.client.ReplicatorClient("hahaha", new StateMachine() {
            @Override
            public void apply(List<byte[]> logs) {
                List<String> logsInStr = logs.stream().map(bs -> new String(bs, StandardCharsets.UTF_8)).collect(Collectors.toList());
                logger.info("apply {} {}", logsInStr, logs.size());
            }

            @Override
            public void snapshot(byte[] snapshot) {
                logger.info("apply snapshot {}", new String(snapshot, StandardCharsets.UTF_8));
            }

            @Override
            public void reset() {
                logger.info("reset called");
            }
        }, options);

        client.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                client.stop();
                logger.info("client shutdown");
            } catch (Exception ex) {
                logger.error("Client graceful shutdown got unexpected exception", ex);
            }
        }));

        Thread.sleep(10000);
    }
}

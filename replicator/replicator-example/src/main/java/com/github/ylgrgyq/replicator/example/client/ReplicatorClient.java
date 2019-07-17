package com.github.ylgrgyq.replicator.example.client;

import com.github.ylgrgyq.replicator.client.ReplicatorClientOptions;
import com.github.ylgrgyq.replicator.client.StateMachine;
import com.github.ylgrgyq.replicator.client.connection.websocket.NettyReplicatorClient;
import com.github.ylgrgyq.replicator.server.connection.websocket.NettyReplicateChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ReplicatorClient {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClient.class);

    public static void main(String[] args) throws Exception{
        ReplicatorClientOptions options = new ReplicatorClientOptions();
        NettyReplicatorClient client = new NettyReplicatorClient("hahaha", new StateMachine() {
            @Override
            public void apply(List<byte[]> logs) {
                List<String> logsInStr = logs.stream().map(bs -> new String(bs, StandardCharsets.UTF_8)).collect(Collectors.toList());
                logger.info("apply {} {}", logsInStr, logs.size());
            }

            @Override
            public void snapshot(byte[] snapshot) {
                logger.info("apply snapshot {}", new String(snapshot, StandardCharsets.UTF_8));
            }
        }, options);


        CompletableFuture<Void> f = client.start();
        f.exceptionally(t -> {
            logger.error("connect to server failed", t);
            return null;
        });

        Thread.sleep(10000);
    }
}

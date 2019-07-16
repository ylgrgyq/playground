package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.client.connection.websocket.NettyReplicatorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class ReplicatorClientBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClientBootstrap.class);

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


        client.start();
        Thread.sleep(10000);
    }
}

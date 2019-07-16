package com.github.ylgrgyq.replicator.example.server;

import com.github.ylgrgyq.replicator.server.*;
import com.github.ylgrgyq.replicator.server.connection.websocket.NettyReplicateChannel;
import com.github.ylgrgyq.replicator.server.connection.websocket.NettyReplicatorServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class ReplicatorServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyReplicateChannel.class);

    public static void main(String[] args) {
        ReplicatorOptions options = new ReplicatorOptions();
        options.setPort(8888);

        NettyReplicatorServer server = new NettyReplicatorServer(options);
        server.start().whenComplete((r, t) -> {
            if (t != null) {
                logger.error("Start replicator failed", t);
            }

            new Thread(() ->{
                SequenceAppender appender = server.createSequenceAppender("hahaha");
                for (int i = 0; i < 10000; ++i) {
                    String msg = "wahaha-" + i;
                    appender.append(i, msg.getBytes(StandardCharsets.UTF_8));
                    try {
//                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        logger.error("exception", ex);
                    }
                }

                logger.info("generate log done {}");
            }).start();
        });
    }
}

package com.github.ylgrgyq.replicator.example.server;

import com.github.ylgrgyq.replicator.server.*;
import com.github.ylgrgyq.replicator.server.connection.netty.NettyReplicateChannel;
import com.github.ylgrgyq.replicator.server.connection.netty.NettyReplicatorServer;
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

            SequenceAppender appender = server.createSequenceAppender("hahaha");

            for (int i = 0; i < 10000; ++i) {
                String msg = "wahaha-" + i;
                appender.append(i, msg.getBytes(StandardCharsets.UTF_8));
            }

            logger.info("generate log done {}");

        });
    }
}

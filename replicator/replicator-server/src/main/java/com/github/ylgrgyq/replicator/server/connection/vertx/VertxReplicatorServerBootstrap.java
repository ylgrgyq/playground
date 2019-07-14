package com.github.ylgrgyq.replicator.server.connection.vertx;

import com.github.ylgrgyq.replicator.server.ReplicatorOptions;
import com.github.ylgrgyq.replicator.server.SequenceAppender;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class VertxReplicatorServerBootstrap extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(VertxReplicatorServerBootstrap.class);

    @Override
    public void start(Future<Void> startFuture) {
//        ReplicatorOptions options = new ReplicatorOptions();
//        options.setPort(8888);
//
//        VertxReplicatorServer server = new VertxReplicatorServer(vertx, options);
//        server.start().whenComplete((r, t) -> {
//            if (t != null) {
//                logger.error("Start replicator failed", t);
//            }
//
//            SequenceAppender appender = server.createSequenceAppender("hahaha");
//
//            for (int i = 0; i < 10000; ++i) {
//                String msg = "wahaha-" + i;
//                appender.append(i, msg.getBytes(StandardCharsets.UTF_8));
//            }
//
//            logger.info("generate log done {}");
//
//        });
    }
}

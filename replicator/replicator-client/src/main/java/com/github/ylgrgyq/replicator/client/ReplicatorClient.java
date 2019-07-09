package com.github.ylgrgyq.replicator.client;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class ReplicatorClient extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClient.class);

    @Override
    public void start(Future<Void> startFuture)  {
        HttpClient client = vertx.createHttpClient();
        client.websocket(8888, "127.0.0.1", "/", socket -> {
            startFuture.complete();
            Subscriber subscriber = new Subscriber(socket, new StateMachine() {
                @Override
                public void apply(List<byte[]> logs) {
                    List<String> logsInStr = logs.stream().map(bs -> new String(bs, StandardCharsets.UTF_8)).collect(Collectors.toList());
                    logger.info("apply {} {}", logsInStr, logs.size());
                }

                @Override
                public void snapshot(byte[] snapshot) {
                    logger.info("apply snapshot {}", new String(snapshot, StandardCharsets.UTF_8));
                }
            });

            subscriber.subscribe("hahaha");
        });
    }
}

package com.github.ylgrgyq.replicator.server;

import java.util.concurrent.CompletableFuture;

public interface ReplicatorServer {
    CompletableFuture<Void> start();
    SequenceAppender createSequenceAppender(String topic);
    void shutdown();
}

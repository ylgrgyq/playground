package com.github.ylgrgyq.replicator.server;

import java.util.concurrent.CompletableFuture;

public interface ReplicatorServer {
    boolean start();
    SequenceAppender createSequence(String topic, SequenceOptions options);
    boolean removeSequence(String topic);
    void shutdown();
}

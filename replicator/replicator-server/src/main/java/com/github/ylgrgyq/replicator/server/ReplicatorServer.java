package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.server.sequence.SequenceAppender;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;

public interface ReplicatorServer {
    SequenceAppender createSequence(String topic, SequenceOptions options);
    void dropSequence(String topic);
    void shutdown() throws InterruptedException;
}

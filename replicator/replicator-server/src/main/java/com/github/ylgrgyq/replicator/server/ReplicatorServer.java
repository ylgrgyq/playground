package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.server.sequence.SequenceAppender;
import com.github.ylgrgyq.replicator.server.sequence.SequenceOptions;
import com.github.ylgrgyq.replicator.server.sequence.SequenceReader;

public interface ReplicatorServer {
    SequenceAppender createSequence(String topic, SequenceOptions options);
    SequenceReader getSequenceReader(String topic);
    void dropSequence(String topic);
    void shutdown() throws InterruptedException;
}

package com.github.ylgrgyq.replicator.server.sequence;

public interface Sequence extends SequenceAppender, SequenceReader {
    void shutdown() throws InterruptedException;

    void drop();
}

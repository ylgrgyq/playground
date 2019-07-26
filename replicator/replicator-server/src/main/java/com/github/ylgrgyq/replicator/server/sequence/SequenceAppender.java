package com.github.ylgrgyq.replicator.server.sequence;

public interface SequenceAppender {
    void append(long id, byte[] log);
}
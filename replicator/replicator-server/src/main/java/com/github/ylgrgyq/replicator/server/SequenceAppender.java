package com.github.ylgrgyq.replicator.server;

public class SequenceAppender {
    private Sequence seq;
    public SequenceAppender(Sequence seq) {
        this.seq = seq;
    }
    public void append(long id, byte[] log) {
        seq.append(id, log);
    }
}
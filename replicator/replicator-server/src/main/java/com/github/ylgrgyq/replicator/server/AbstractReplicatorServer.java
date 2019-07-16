package com.github.ylgrgyq.replicator.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public abstract class AbstractReplicatorServer implements ReplicatorServer {
    private SequenceGroups groups;

    public AbstractReplicatorServer() {
        this.groups = new SequenceGroups();
    }

    protected SequenceGroups getGroups() {
        return groups;
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void shutdown() {}

    @Override
    public SequenceAppender createSequenceAppender(String topic) {
        SequenceOptions op = new SequenceOptions();
        op.setSequenceExecutor(Executors.newSingleThreadExecutor());
        Sequence seq = groups.createSequence(topic, op);
        return new SequenceAppender(seq);
    }
}

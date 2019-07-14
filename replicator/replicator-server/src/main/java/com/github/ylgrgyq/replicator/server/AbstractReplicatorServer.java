package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.proto.ReplicatorCommand;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public abstract class AbstractReplicatorServer implements ReplicatorServer {
    private SequenceGroups groups;

    public AbstractReplicatorServer() {
        this.groups = new SequenceGroups();
    }

    protected void handleRequest(ReplicateChannel channel, ReplicatorCommand cmd, ReplicateRequestHandler handler) {
        try {
            switch (cmd.getType()) {
                case HANDSHAKE:
                    String topic = cmd.getTopic();

                    Sequence seq = groups.getSequence(topic);
                    if (seq == null) {
                        seq = groups.createSequence(topic, new SequenceOptions());
                    }

                    handler.onStart(topic, seq);
                    break;
                case GET:
                    long fromIndex = cmd.getFromIndex();
                    int limit = cmd.getLimit();

                    handler.heandleSyncLogs(fromIndex, limit);
                    break;
                case SNAPSHOT:
                    handler.handleSyncSnapshot();
                    break;
            }
        } catch (ReplicatorException ex) {
            channel.writeError(ex.getError());
        }
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public SequenceAppender createSequenceAppender(String topic) {
        SequenceOptions op = new SequenceOptions();
        op.setSequenceExecutor(Executors.newSingleThreadExecutor());
        Sequence seq = groups.createSequence(topic, op);
        return new SequenceAppender(seq);
    }
}
